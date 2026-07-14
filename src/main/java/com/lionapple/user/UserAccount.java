package com.lionapple.user;

import java.time.LocalDateTime;

import com.lionapple.user.dto.GoogleUserInfo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_accounts")
public class UserAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String googleSubject;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String name;

    private String pictureUrl;

    @Column(nullable = false)
    private LocalDateTime lastLoginAt;

    protected UserAccount() {
    }

    public UserAccount(GoogleUserInfo googleUserInfo) {
        this.googleSubject = googleUserInfo.subject();
        updateLoginInfo(googleUserInfo);
    }

    public Long getId() {
        return id;
    }

    public String getGoogleSubject() {
        return googleSubject;
    }

    public String getEmail() {
        return email;
    }

    public String getName() {
        return name;
    }

    public void updateLoginInfo(GoogleUserInfo googleUserInfo) {
        this.email = googleUserInfo.email();
        this.name = googleUserInfo.name() == null || googleUserInfo.name().isBlank()
                ? googleUserInfo.email()
                : googleUserInfo.name();
        this.pictureUrl = googleUserInfo.pictureUrl();
        this.lastLoginAt = LocalDateTime.now();
    }
}
