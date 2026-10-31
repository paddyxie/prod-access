package io.prodaccess.control.client;

import io.prodaccess.common.protocol.RunSpec;

/** Resolves a Client's credential reference to the secret. The production implementation reads Vault. */
public interface CredentialProvider {
    RunSpec.ClientAuth resolve(String credentialRef);
}
