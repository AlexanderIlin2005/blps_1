package ru.sashil.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserRegistrationDTO {
    private String username;
    private String password;
    private String confirmPassword;
    private String email;
    private String fullName;
    private String phone;
}
