package com.example.ssh.demo;

import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ClientChannel;
import org.apache.sshd.client.channel.ClientChannelEvent;
import org.apache.sshd.client.future.AuthFuture;
import org.apache.sshd.client.future.ConnectFuture;
import org.apache.sshd.client.future.OpenFuture;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.channel.Channel;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
public class SshUtils {

    private SshUtils() {
    }

    public static SshResult sendExecCommand(Map<String, Object> headers, String command, SshConfiguration configuration, SshClient client)
            throws Exception {

        String userName = configuration.username();
        ConnectFuture connectFuture = client.connect(userName, configuration.hostname(), configuration.port());
        // wait getTimeout milliseconds for connect operation to complete
        connectFuture.await(configuration.timeout());

        if (!connectFuture.isDone() || !connectFuture.isConnected()) {
            throw new RuntimeException(
                    "Failed to connect to " + configuration.hostname() + ":" + configuration.port() + " within timeout "
                            + configuration.timeout() + "ms");
        }

        log.debug("Connected to {}:{}", configuration.hostname(), configuration.port());

        ClientChannel channel = null;
        ClientSession session = null;

        try {
            AuthFuture authResult;
            session = connectFuture.getSession();

            // either provide a keypair or password identity first
            String password = configuration.password();


            log.debug("Attempting to authenticate username '{}' using a password identity", userName);
            session.addPasswordIdentity(password);

            // now start the authentication process
            authResult = session.auth();

            authResult.await(configuration.timeout());

            if (!authResult.isDone() || authResult.isFailure()) {
                log.debug("Failed to authenticate");
                throw new RuntimeException("Failed to authenticate username " + configuration.username());
            }

            InputStream in;
            SshResult result;
            channel = session.createChannel(Channel.CHANNEL_EXEC, command);
            in = new ByteArrayInputStream(new byte[]{0});

            channel.setIn(in);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            channel.setOut(out);

            ByteArrayOutputStream err = new ByteArrayOutputStream();
            channel.setErr(err);
            OpenFuture openFuture = channel.open();
            openFuture.await(configuration.timeout());
            result = null;
            if (openFuture.isOpened()) {
                Set<ClientChannelEvent> events = channel.waitFor(List.of(ClientChannelEvent.CLOSED), 0);
                if (!events.contains(ClientChannelEvent.TIMEOUT)) {
                    result = new SshResult(
                            command, channel.getExitStatus(),
                            new ByteArrayInputStream(out.toByteArray()),
                            new ByteArrayInputStream(err.toByteArray()));
                }
            }
            return result;
        } finally {
            if (channel != null) {
                channel.close(true);
            }
            // need to make sure the session is closed
            if (session != null) {
                session.close(false);
            }
        }
    }
}
