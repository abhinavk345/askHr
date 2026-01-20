package com.intech.ai.modal;


import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "employee")
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", unique = true, nullable = false)
    private String employeeId;

    @Column(nullable = false)
    private String password;

    private String fullName;
    private String email;
    private String status;

    /*
    INSERT INTO EMPLOYEE VALUES (1, 'abhinav@example.com', '273273', 'abhinav kumar', '273273', 'ACTIVE'));
      */
}