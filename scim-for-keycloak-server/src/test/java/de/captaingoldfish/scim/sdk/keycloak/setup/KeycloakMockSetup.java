package de.captaingoldfish.scim.sdk.keycloak.setup;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jakarta.persistence.EntityManager;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.keycloak.Config;
import org.keycloak.common.ClientConnection;
import org.keycloak.credential.CredentialProvider;
import org.keycloak.credential.PasswordCredentialProvider;
import org.keycloak.credential.PasswordCredentialProviderFactory;
import org.keycloak.credential.hash.PasswordHashProvider;
import org.keycloak.credential.hash.Pbkdf2PasswordHashProviderFactory;
import org.keycloak.events.EventStoreProvider;
import org.keycloak.events.jpa.JpaEventStoreProvider;
import org.keycloak.executors.DefaultExecutorsProviderFactory;
import org.keycloak.executors.ExecutorsProvider;
import org.keycloak.models.ClientModel;
import org.keycloak.models.KeycloakContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.PasswordPolicy;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserProvider;
import org.keycloak.models.jpa.JpaRealmProvider;
import org.keycloak.models.jpa.JpaUserProvider;
import org.keycloak.policy.DefaultPasswordPolicyManagerProvider;
import org.keycloak.policy.PasswordPolicyManagerProvider;
import org.keycloak.services.DefaultKeycloakContext;
import org.keycloak.services.DefaultKeycloakSessionFactory;
import org.keycloak.services.DefaultKeycloakTransactionManager;
import org.keycloak.storage.DatastoreProvider;
import org.mockito.Mockito;


/**
 * @author Pascal Knueppel
 * @since 05.08.2020
 */
@Slf4j
class KeycloakMockSetup
{

  private static final String TEST_REALM_NAME = "SCIM";

  /**
   * a keycloak session mock
   */
  private KeycloakSession keycloakSession;

  /**
   * used to setup some default database settings
   */
  private EntityManager entityManager;

  /**
   * a context that is placed within the keycloakSession
   */
  private KeycloakContext keycloakContext;

  /**
   * the custom realm for our unit tests
   */
  @Getter
  private RealmModel realmModel;

  /**
   * the user that is used for unit testing
   */
  @Getter
  private UserModel user;

  /**
   * a client used for the {@link org.keycloak.services.resources.admin.AdminAuth} mock and
   * {@link org.keycloak.services.resources.admin.AdminEventBuilder}
   */
  @Getter
  private ClientModel client;

  /**
   * the keycloak session factory
   */
  @Getter
  private KeycloakSessionFactory keycloakSessionFactory;

  /**
   * the transaction manager that is used by keycloak
   */
  @Getter
  private DefaultKeycloakTransactionManager keycloakTransactionManager;

  /**
   * the event store provider used for accessing admin events within the database
   */
  @Getter
  private EventStoreProvider eventStoreProvider;

  public KeycloakMockSetup(KeycloakSession keycloakSession, EntityManager entityManager)
  {
    this.keycloakSession = keycloakSession;
    this.entityManager = entityManager;
    // this.keycloakContext = Mockito.spy(new DefaultKeycloakContext(keycloakSession));
    Mockito.doReturn(keycloakContext).when(this.keycloakSession).getContext();
    // this.keycloakSessionFactory = Mockito.spy(new DefaultKeycloakSessionFactory());
    Mockito.doReturn(keycloakSessionFactory).when(this.keycloakSession).getKeycloakSessionFactory();
    keycloakTransactionManager = Mockito.spy(new DefaultKeycloakTransactionManager(keycloakSession));
    Mockito.doReturn(keycloakTransactionManager).when(keycloakSession).getTransactionManager();
    Mockito.doReturn(keycloakSession).when(keycloakSessionFactory).create();

    JpaRealmProvider jpaRealmProvider = (JpaRealmProvider)keycloakSession.realms();
    Mockito.doReturn(jpaRealmProvider).when(keycloakSession).roles();
    Mockito.doReturn(jpaRealmProvider).when(keycloakSession).groups();

    ClientConnection clientConnection = Mockito.mock(ClientConnection.class);
    Mockito.doReturn(clientConnection).when(keycloakContext).getConnection();

    eventStoreProvider = new JpaEventStoreProvider(this.keycloakSession, this.entityManager);
    Mockito.doReturn(eventStoreProvider).when(this.keycloakSession).getProvider(EventStoreProvider.class);

    setupPasswordManagingSettings();
    mockExecutorService();
  }

  /**
   * mocks the keycloak executor service introduced with keycloak 12
   */
  protected void mockExecutorService()
  {
    DefaultExecutorsProviderFactory executorsProviderFactory = new DefaultExecutorsProviderFactory();
    Config.Scope config = new Config.SystemPropertiesScope("");
    executorsProviderFactory.init(config);
    ExecutorsProvider executorsProvider = executorsProviderFactory.create(keycloakSession);
    Mockito.doReturn(executorsProvider).when(keycloakSession).getProvider(ExecutorsProvider.class);
  }

  /**
   * setups the password managing configuration for testing
   */
  protected void setupPasswordManagingSettings()
  {
    JpaUserProvider jpaUserProvider = new JpaUserProvider(keycloakSession, entityManager);
    Mockito.doReturn(jpaUserProvider).when(this.keycloakSession).users();
    // LegacyStoreManagers legacyStoreManagers = new LegacyDatastoreProvider(null, keycloakSession);
    // Mockito.doReturn(legacyStoreManagers).when(this.keycloakSession).getProvider(DatastoreProvider.class);
    Mockito.doReturn(jpaUserProvider).when(this.keycloakSession).getProvider(UserProvider.class);
    // provider password credential provider factory streams for password tests
    {
      List<Stream<PasswordCredentialProviderFactory>> passwordCredentialProviderStreams = new ArrayList<>();
      // 20 is just a random number that should exceed the number that is actually needed
      for ( int i = 0 ; i < 20 ; i++ )
      {
        passwordCredentialProviderStreams.add(Stream.of(new PasswordCredentialProviderFactory()));
      }
      Mockito.doReturn(Stream.of(new PasswordCredentialProviderFactory()), passwordCredentialProviderStreams.toArray())
             .when(keycloakSessionFactory)
             .getProviderFactoriesStream(CredentialProvider.class);
    }
    Mockito.doReturn(new PasswordCredentialProvider(keycloakSession))
           .when(keycloakSession)
           .getProvider(CredentialProvider.class, PasswordCredentialProviderFactory.PROVIDER_ID);
    Mockito.doReturn(new DefaultPasswordPolicyManagerProvider(keycloakSession))
           .when(keycloakSession)
           .getProvider(PasswordPolicyManagerProvider.class);
    PasswordHashProvider passwordHashProvider = new Pbkdf2PasswordHashProviderFactory().create(keycloakSession);
    Mockito.doReturn(passwordHashProvider)
           .when(keycloakSession)
           .getProvider(PasswordHashProvider.class, "pbkdf2-sha256");
    Mockito.doReturn(passwordHashProvider).when(keycloakSession).getProvider(PasswordHashProvider.class, "pbkdf2");
  }

  /**
   * will create the realm that we are going to use
   */
  public final void createRealm()
  {
    log.trace("building test realm '{}'", TEST_REALM_NAME);
    entityManager.getTransaction().begin();
    realmModel = keycloakSession.realms().createRealm(UUID.randomUUID().toString(), TEST_REALM_NAME);
    realmModel.setAdminEventsEnabled(true);
    realmModel.setAdminEventsDetailsEnabled(true);
    RoleModel roleModel = realmModel.addRole("default-role");
    realmModel.setDefaultRole(roleModel);
    realmModel.setPasswordPolicy(PasswordPolicy.build().build(keycloakSession));
    Mockito.doReturn(realmModel).when(keycloakContext).getRealm();
    List<RealmModel> realms = keycloakSession.realms().getRealmsStream().collect(Collectors.toList());
    Assertions.assertEquals(1, realms.size());
    log.debug("test-realm successfully created: {} - {}", realmModel.getId(), realmModel.getName());
    createUser();
    createClient();
    entityManager.getTransaction().commit();
  }

  private void createClient()
  {
    client = realmModel.addClient("goldfish");
  }

  /**
   * creates a user for the current realm
   */
  private void createUser()
  {
    user = keycloakSession.users().addUser(realmModel, "admin");
  }


}
