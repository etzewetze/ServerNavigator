package io.github.etzewetze.servernavigator;
import org.junit.jupiter.api.Test;
import com.velocitypowered.api.proxy.Player;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class IntegrationContractTest {
 @Test void optionalIntegrationUsesOnlyPublicProxyAndJdkTypes()throws Exception{
  assertEquals(int.class,ServerNavigatorPlugin.class.getMethod("serverHubApiVersion").getReturnType());
  assertEquals(boolean.class,ServerNavigatorPlugin.class.getMethod("requestServer",Player.class,String.class).getReturnType());
  assertEquals(String.class,ServerNavigatorPlugin.class.getMethod("navigationPhase",String.class).getReturnType());
  assertEquals(boolean.class,ServerNavigatorPlugin.class.getMethod("isWaiting",UUID.class).getReturnType());
 }
}
