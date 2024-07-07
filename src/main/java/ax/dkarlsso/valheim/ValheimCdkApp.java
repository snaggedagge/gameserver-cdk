package ax.dkarlsso.valheim;

import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.StackProps;

public class ValheimCdkApp {


    public static void main(final String[] args) {
        App app = new App();

/*
        new ValheimServerStack(app, "valheim-server", StackProps.builder()
                .env(Environment.builder()
                        .region("eu-north-1")
                        .account("145158422295")
                        .build())
                .build());
 */

        new TempoEnshroudedServerStack(app, "enshrouded-server", StackProps.builder()
                .env(Environment.builder()
                        .region("eu-north-1")
                        .account("145158422295")
                        .build())
                .build());
        app.synth();
    }
}
