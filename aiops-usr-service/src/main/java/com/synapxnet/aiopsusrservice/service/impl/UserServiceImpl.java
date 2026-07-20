package com.synapxnet.aiopsusrservice.service.impl;

import com.synapxnet.aiopsusrservice.entity.User;
import com.synapxnet.aiopsusrservice.mapper.UserMapper;
import com.synapxnet.aiopsusrservice.service.UserService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UserServiceImpl(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public List<User> listAll() {
        return userMapper.findAll();
    }

    @Override
    public User getById(Long id) {
        User user = userMapper.findById(id);
        if (user == null) {
            throw new IllegalArgumentException("User not found: " + id);
        }
        return user;
    }

    @Override
    public User create(User user) {
        user.setUid(UUID.randomUUID().toString());
        if (user.getStatus() == null) {
            user.setStatus("active");
        }
        if (user.getUserType() == null) {
            user.setUserType("user");
        }
        if (user.getPassword() != null) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        }
        userMapper.insert(user);
        return user;
    }

    @Override
    public User update(User user) {
        userMapper.update(user);
        return userMapper.findById(user.getId());
    }

    @Override
    public void delete(Long id) {
        userMapper.deleteSessionsByUserId(id);
        userMapper.deleteById(id);
    }

    @Override
    public void changePassword(Long id, String oldPassword, String newPassword) {
        User user = getById(id);
        boolean match = user.getPassword().startsWith("$2")
                ? passwordEncoder.matches(oldPassword, user.getPassword())
                : oldPassword.equals(user.getPassword());
        if (!match) {
            throw new IllegalArgumentException("Old password is incorrect");
        }
        userMapper.updatePassword(id, passwordEncoder.encode(newPassword));
    }
}
