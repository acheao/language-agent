package com.acheao.languageagent.dto.res;

public class AuthRes {
    private String token;
    private String username;

    public AuthRes() {
    }

    public AuthRes(String token, String username) {
        this.token = token;
        this.username = username;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }
}
