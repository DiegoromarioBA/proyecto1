package com.bar.service;

import com.bar.model.Dish;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface IDishService extends ICRUD<Dish, String>{

    //Flux<Dish> getExpensiveDishes();

    /*Mono<Dish> save(Dish dish);
    Mono<Dish> update(String id, Dish dish);
    Flux<Dish> findAll();
    Mono<Dish> findById(String id);
    Mono<Boolean> delete(String id);*/

}
