package com.intech.ai.modal;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Entity
@Table(name = "leave_balance")
public class LeaveBalance {

  @Id
  private String employeeId;

  private int casualLeaves;
  private int sickLeaves;
}
