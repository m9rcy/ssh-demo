package com.example.ssh.demo;

public record SshConfiguration(String hostname, Integer port, String username, String password, Long timeout) {
}
