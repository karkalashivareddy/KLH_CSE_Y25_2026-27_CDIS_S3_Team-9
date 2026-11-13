package com.cloudresilience.security;

import com.cloudresilience.modules.identity.application.UserPrincipal;
import com.cloudresilience.modules.identity.domain.RoleName;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.security.jackson.SecurityJacksonModules;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

@Configuration(proxyBeanMethods = false)
public class SessionSerializationConfiguration {
    @Bean(name = "springSessionDefaultRedisSerializer")
    RedisSerializer<Object> springSessionDefaultRedisSerializer() {
        // Spring Security contributes its Jackson modules and allowlist entries. Add only
        // the application's session principal and role enum; do not enable unrestricted typing.
        BasicPolymorphicTypeValidator.Builder validator = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType(UserPrincipal.class)
                .allowIfSubType(RoleName.class)
                .allowIfSubType("java.util.ArrayList")
                .allowIfSubType("java.util.LinkedList")
                .allowIfSubType("java.util.LinkedHashMap")
                .allowIfSubType("java.util.HashMap")
                .allowIfSubType("java.util.ImmutableCollections$");
        List<tools.jackson.databind.JacksonModule> modules = SecurityJacksonModules.getModules(
                SessionSerializationConfiguration.class.getClassLoader(), validator);
        return GenericJacksonJsonRedisSerializer.builder()
                .customize(mapperBuilder -> mapperBuilder.addModules(modules))
                .build();
    }
}
