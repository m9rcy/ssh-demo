package com.example.ssh.demo;

import java.io.InputStream;

public record SshResult(String command, Integer exitCode, InputStream stdout, InputStream stderr) {
}
