package com.margomarket.service;
import com.margomarket.model.User;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
class PublicNamesTest {
 @Test void hidesLegacyEmailAndMissingNames(){User u=new User();u.setId(5L);u.setDisplayName("mail@example.com");assertThat(PublicNames.name(u)).isEqualTo("Gracz 5");u.setDisplayName(null);assertThat(PublicNames.name(u)).isEqualTo("Gracz 5");}
 @Test void preservesPublicDisplayName(){User u=new User();u.setDisplayName("Wojownik");assertThat(PublicNames.name(u)).isEqualTo("Wojownik");}
}
