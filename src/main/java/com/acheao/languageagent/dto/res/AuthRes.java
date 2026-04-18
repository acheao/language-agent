package com.acheao.languageagent.dto.res;

public class AuthRes {
    private String token;
    private UserProfileRes user;

    public AuthRes() {
    }

    public AuthRes(String token, UserProfileRes user) {
        this.token = token;
        this.user = user;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public UserProfileRes getUser() {
        return user;
    }

    public void setUser(UserProfileRes user) {
        this.user = user;
    }
}
