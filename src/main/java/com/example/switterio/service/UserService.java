package com.example.switterio.service;

import com.example.switterio.config.EncryptConfig;
import com.example.switterio.domain.Role;
import com.example.switterio.domain.User;
import com.example.switterio.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;

    private final SpringMailSenderService springMailSender;
    private final EncryptConfig encryptConfig;
    @Value("${hostname}")
    private String hostname;

    public UserService(UserRepository userRepository, SpringMailSenderService springMailSender, EncryptConfig encryptConfig) {
        this.userRepository = userRepository;
        this.springMailSender = springMailSender;
        this.encryptConfig = encryptConfig;
    }


    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username);
        if (user == null) {
            throw new UsernameNotFoundException("User not found");
        }
        return user;
    }

    public boolean addUser(User user) {
        User userFromDb = userRepository.findByUsername(user.getUsername());

        if (userFromDb != null) {
            return false;
        }

        user.setActive(true);
        user.setRoles(Collections.singleton(Role.USER));
        user.setActivationCode(UUID.randomUUID().toString());
        user.setPassword(encryptConfig.getPasswordEncoder().encode(user.getPassword()));

        userRepository.save(user);

        sendMessage(user);
        return true;
    }

    private void sendMessage(User user) {
        if (StringUtils.hasLength(user.getEmail())) {
            String message = String.format(
                    "Hello, %s! \n" +
                            "Welcome to SwitterIO. Please, visit next link: http://%s/activate/%s",
                    user.getUsername(),
                    hostname,
                    user.getActivationCode()
            );

            springMailSender.send(user.getEmail(), "Activation code", message);
        }
    }

    public boolean activateUser(String code) {
        User user = userRepository.findByActivationCode(code);

        if (user == null) {
            return false;
        }

        user.setActivationCode(null);

        userRepository.save(user);

        return true;
    }

    public List<User> findAll() {
        return userRepository.findAll();
    }

    public void saveUser(User user, String username, Map<String, String> form) {
        user.setUsername(username);

        Set<String> roles = Arrays.stream(Role.values())
                .map(Role::name)
                .collect(Collectors.toSet());

        user.getRoles().clear();

        for (String key : form.keySet()) {
            if (roles.contains(key)) {
                user.getRoles().add(Role.valueOf(key));
            }
        }

        userRepository.save(user);
    }

    public void updateProfile(User user, String password, String email) {
        String userEmail = user.getEmail();

        boolean isEmailChanged = (email != null && !email.equals(userEmail)) ||
                (userEmail != null && !userEmail.equals(email));

        if (isEmailChanged) {
            user.setEmail(email);

            if (StringUtils.hasLength(email)) {
                user.setActivationCode(UUID.randomUUID().toString());
            }
        }
        if (StringUtils.hasLength(password)) {
            user.setPassword(encryptConfig.getPasswordEncoder().encode(password));
        }
        userRepository.save(user);

        if (isEmailChanged) {
            sendMessage(user);
        }
    }

    public void subscribe(User currentUser, User user) {
        user.getSubscribers().add(currentUser);
        userRepository.save(user);
    }

    public void unsubscribe(User currentUser, User user) {
        user.getSubscriptions().remove(currentUser);
        userRepository.save(user);
    }
}
