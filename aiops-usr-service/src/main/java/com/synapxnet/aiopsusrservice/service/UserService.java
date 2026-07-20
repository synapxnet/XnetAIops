package com.synapxnet.aiopsusrservice.service;

import com.synapxnet.aiopsusrservice.entity.User;
import java.util.List;

public interface UserService {

    List<User> listAll();

    User getById(Long id);

    User create(User user);

    User update(User user);

    void delete(Long id);

    void changePassword(Long id, String oldPassword, String newPassword);
}
