package io.prodaccess.control.client;

import io.prodaccess.common.protocol.RunSpec;
import io.prodaccess.control.config.ProdAccessProperties;
import org.springframework.stereotype.Component;

/** Dev placeholder: credentials from {@code prodaccess.credentials.<ref>}. Replace with a Vault-backed provider. */
@Component
class ConfigCredentialProvider implements CredentialProvider {
    private final ProdAccessProperties props;

    ConfigCredentialProvider(ProdAccessProperties props) {
        this.props = props;
    }

    @Override
    public RunSpec.ClientAuth resolve(String credentialRef) {
        ProdAccessProperties.Credential c = props.credentials().get(credentialRef);
        if (c == null) throw new IllegalStateException("no credentials for ref '" + credentialRef + "'");
        return new RunSpec.ClientAuth(c.clientId(), c.clientSecret(), c.scope());
    }
}
