package ax.dkarlsso.game_management;

import ax.dkarlsso.game_management.features.EbsDiscFeature;
import ax.dkarlsso.game_management.features.Route53Feature;
import ax.dkarlsso.game_management.games.Enshrouded;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.Size;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.ec2.InstanceClass;
import software.amazon.awscdk.services.ec2.InstanceSize;
import software.amazon.awscdk.services.ec2.InstanceType;
import software.amazon.awscdk.services.route53.HostedZoneAttributes;

import java.util.List;
import java.util.Optional;

public class CdkApp {
    public static void main(final String[] args) {
        App app = new App();
        final var props = StackProps.builder()
                .env(Environment.builder()
                        .region("eu-north-1")
                        .account("145158422295")
                        .build())
                .build();

        final var zoneAttributes = HostedZoneAttributes.builder()
                .hostedZoneId("Z2URP9ATDI0RG5")
                .zoneName("dkarlsso.com")
                .build();

        final List<GameServerStack> gameServerStacks = List.of(
                GameServerStack.create(app, props,
                        Enshrouded.builder()
                                .serverName("SnaGGShroud")
                                .serverPassword("secret-pass") // Should come from SecretsManager
                                .gameServerId("enshrouded")
                                .instanceType(InstanceType.of(InstanceClass.BURSTABLE3, InstanceSize.XLARGE))
                                .build(),
                        List.of(new EbsDiscFeature(Size.gibibytes(20)), new Route53Feature(zoneAttributes, "enshrouded.dkarlsso.com")),
                        List.of("eu-north-1c"))
/*
                GameServerStack.create(app, props,
                        Valheim.builder()
                                .serverName("SnaGGHeim")
                                .serverPassword("secret-pass") // Should come from SecretsManager
                                .gameServerId("valheim")
                                .instanceType(InstanceType.of(InstanceClass.BURSTABLE3, InstanceSize.MEDIUM))
                                .build(),
                        List.of(new EbsDiscFeature(Size.gibibytes(10)), new Route53Feature(zoneAttributes, "valheim.dkarlsso.com")),
                        List.of("eu-north-1c")),
                GameServerStack.create(app, props,
                        Valheim.builder()
                                .serverName("Ronheim")
                                .serverPassword("secret-pass") // Should come from SecretsManager
                                .gameServerId("ronnys-valheim")
                                .instanceType(InstanceType.of(InstanceClass.BURSTABLE3, InstanceSize.LARGE))
                                .build(),
                        List.of(new EfsDiscFeature(), new Route53Feature(zoneAttributes, "ronheim.dkarlsso.com")),
                        List.of("eu-north-1a", "eu-north-1b", "eu-north-1c"))
 */
        );
        new GameManagementStack(app, "game-management", props, Optional.of(zoneAttributes), gameServerStacks);
        app.synth();
    }
}
