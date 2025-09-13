package backend.consutalar_correo.services;

import java.util.Optional;

public interface EmailProcessorService {

    Optional<String> extractNetflixHomeLink(String email);

    Optional<String> extractTemporaryCode(String email);

    boolean validateEmailConnection(String email);
}
