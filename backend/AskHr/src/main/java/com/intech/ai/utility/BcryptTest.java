package com.intech.ai.utility;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class BcryptTest {

    public static void main(String[] args) {

        System.out.println(new BCryptPasswordEncoder().encode("273273"));
        System.out.println("$2a$10$7QbJwA0pI4GZ6ZJfP1qQcuFZbLoVz3uD32YjK0t4nWj3rWqA3T3dC");
        System.out.println(new BCryptPasswordEncoder().encode("273273").equals("$2a$10$7QbJwA0pI4GZ6ZJfP1qQcuFZbLoVz3uD32YjK0t4nWj3rWqA3T3dC"));
    }
}
