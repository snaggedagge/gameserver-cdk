package ax.dkarlsso.game_management;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public final class Scripts {

    public static final String ENVIRONMENT_VARIABLES_SCRIPT_NAME = "environment-variables.sh";
    public static final String ENVIRONMENT_VARIABLES_SCRIPT_FOLDER = "/etc/profile.d/";
    public static final String ENVIRONMENT_VARIABLES_SCRIPT_PATH = ENVIRONMENT_VARIABLES_SCRIPT_FOLDER + ENVIRONMENT_VARIABLES_SCRIPT_NAME;
    private final List<String> startup = new ArrayList<>(List.of(
            "#!/bin/bash",
            "source " + ENVIRONMENT_VARIABLES_SCRIPT_PATH,
            """
            yum install docker -y
            systemctl start docker
            curl --silent --location https://rpm.nodesource.com/setup_20.x | bash -
            yum -y install nodejs
            npm install -g gamedig
            yum install cronie -y
            systemctl enable crond.service
            systemctl start crond.service
            crontab -l > mycron
            echo "*/30 * * * * /tmp/check-players.sh" >> mycron
            crontab mycron
            rm mycron
                            """));

    private final List<String> environment = new ArrayList<>(List.of(
            "#!/bin/sh",
            "export TOKEN=$(curl -X PUT \"http://169.254.169.254/latest/api/token\" -H \"X-aws-ec2-metadata-token-ttl-seconds: 21600\")",
            "export IP=$(curl -H \"X-aws-ec2-metadata-token: $TOKEN\" http://169.254.169.254/latest/meta-data/public-ipv4)"
    ));

    public void addToStartupScript(String... lines) {
        startup.addAll(Arrays.stream(lines).toList());
    }

    public void addToEnvironmentScript(String... lines) {
        environment.addAll(Arrays.stream(lines).toList());
    }

    public String getStartupScript() {
        return String.join("\n", startup.stream().flatMap(line -> Arrays.stream(line.split("\n"))).toList());
    }

    public String getEnvironmentScript() {
        return String.join("\n", environment.stream().flatMap(line -> Arrays.stream(line.split("\n"))).toList());
    }
}
