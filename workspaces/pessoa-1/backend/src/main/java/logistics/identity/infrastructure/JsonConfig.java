package logistics.identity.infrastructure;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.cfg.*;
import com.fasterxml.jackson.databind.type.LogicalType;
import java.time.Instant;
import java.time.format.DateTimeFormatterBuilder;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.*;

@Configuration
class JsonConfig {
  @Bean
  Jackson2ObjectMapperBuilderCustomizer strictJson() {
    return builder ->
        builder.postConfigurer(
            mapper -> {
              mapper
                  .coercionConfigFor(LogicalType.Textual)
                  .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                  .setCoercion(CoercionInputShape.Float, CoercionAction.Fail);
              var module = new com.fasterxml.jackson.databind.module.SimpleModule();
              module.addSerializer(
                  Instant.class,
                  new com.fasterxml.jackson.databind.JsonSerializer<Instant>() {
                    public void serialize(
                        Instant value,
                        com.fasterxml.jackson.core.JsonGenerator gen,
                        com.fasterxml.jackson.databind.SerializerProvider serializers)
                        throws java.io.IOException {
                      gen.writeString(
                          new DateTimeFormatterBuilder()
                              .appendInstant(3)
                              .toFormatter()
                              .format(value));
                    }
                  });
              mapper.registerModule(module);
            });
  }
}
