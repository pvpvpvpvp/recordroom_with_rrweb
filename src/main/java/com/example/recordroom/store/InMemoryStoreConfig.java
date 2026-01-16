package com.example.recordroom.store;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class InMemoryStoreConfig {

    @Bean
    public InMemoryStores.RecordStore recordStore() {
        return new InMemoryStores.RecordStore();
    }

    @Bean
    public InMemoryStores.EventStore eventStore() {
        return new InMemoryStores.EventStore();
    }
}
