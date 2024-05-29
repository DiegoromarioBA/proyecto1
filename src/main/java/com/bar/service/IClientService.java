package com.bar.service;


import com.bar.model.Client;
import reactor.core.publisher.Flux;

public interface IClientService extends ICRUD<Client, String> {

    //Flux<Client> getClientsAdults();
}
