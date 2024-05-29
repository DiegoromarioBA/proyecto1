package com.bar.controller;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.bar.dto.ClientDTO;
import com.bar.model.Client;
import com.bar.pagination.PageSupport;
import com.bar.service.IClientService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.cloudinary.json.JSONObject;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.Link;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.util.Map;

import static org.springframework.hateoas.server.reactive.WebFluxLinkBuilder.linkTo;
import static org.springframework.hateoas.server.reactive.WebFluxLinkBuilder.methodOn;

@RestController
@RequestMapping("/clients")
@RequiredArgsConstructor
public class ClientController {

    private final IClientService service;
    @Qualifier("clientMapper")
    private final ModelMapper modelMapper;
    private final Cloudinary cloudinary;

    @GetMapping
    public Mono<ResponseEntity<Flux<ClientDTO>>> findAll() {
        Flux<ClientDTO> fx = service.findAll().map(this::convertToDto);

        return Mono.just(ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(fx)
        ).defaultIfEmpty(ResponseEntity.notFound().build());

    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<ClientDTO>> findById(@PathVariable("id") String id) {
        return service.findById(id)
                .map(this::convertToDto)
                .map(e -> ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(e)
                )
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PostMapping
    public Mono<ResponseEntity<ClientDTO>> save(@Valid @RequestBody ClientDTO dto, final ServerHttpRequest req) {
        return service.save(convertToDocument(dto))
                .map(this::convertToDto)
                .map(e -> ResponseEntity.created(
                                        URI.create(req.getURI().toString().concat("/").concat(e.getId()))
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(e)
                ).defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public Mono<ResponseEntity<ClientDTO>> update(@Valid @PathVariable("id") String id, @RequestBody ClientDTO dto) {
        return Mono.just(dto)
                .map(e -> {
                    e.setId(id);
                    return e;
                })
                .flatMap(e -> service.update(id, convertToDocument(dto)))
                .map(this::convertToDto)
                .map(e -> ResponseEntity
                        .ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(e)
                ).defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> delete(@PathVariable("id") String id) {
        return service.delete(id)
                .flatMap(result -> {
                    if (result) {
                        return Mono.just(ResponseEntity.noContent().build());
                    } else {
                        return Mono.just(ResponseEntity.notFound().build());
                    }
                });
    }

    private ClientDTO clientHateoas;

    @GetMapping("/hateoas/{id}")
    public Mono<EntityModel<ClientDTO>> getHateoas(@PathVariable("id") String id) {
        Mono<Link> monoLink = linkTo(methodOn(ClientController.class).findById(id)).withRel("client-info").toMono();

        //PRACTICA ES COMUN PERO NO RECOMENDADA
        /*return service.findById(id)
                .map(this::convertToDto)
                .flatMap(d -> {
                    this.clientHateoas = d;
                    return monoLink;
                })
                .map(link -> EntityModel.of(this.clientHateoas,link));*/

        //PRACTICA INTERMEDIA
        /*return service.findById(id)
                .map(this::convertToDto)
                .flatMap(d -> monoLink.map(link -> EntityModel.of(d, link)));*/

        //PRACTICA IDEAL
        return service.findById(id)
                .map(this::convertToDto)
                .zipWith(monoLink, EntityModel::of); // (d, link) -> EntityModel.of(d, link)
    }

    @GetMapping("/pageable")
    public Mono<ResponseEntity<PageSupport<ClientDTO>>> getPage(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "2") int size
    ) {

        return service.getPage(PageRequest.of(page, size))
                .map(pageSupport -> new PageSupport<>(
                        pageSupport.getContent().stream().map(this::convertToDto).toList(),
                        pageSupport.getPageNumber(),
                        pageSupport.getPageSize(),
                        pageSupport.getTotalElements()
                ))
                .map(e -> ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(e)
                )
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PostMapping("/v1/upload/{id}")
    public Mono<ResponseEntity<ClientDTO>> uploadV1(@PathVariable("id") String id, @RequestPart("file") FilePart filePart) {
        return service.findById(id)
                .flatMap(client -> {
                    try {
                        File f = Files.createTempFile("temp", filePart.filename()).toFile();
                        filePart.transferTo(f).block();

                        Map response = cloudinary.uploader().upload(f, ObjectUtils.asMap("resource_type", "auto"));
                        JSONObject json = new JSONObject(response);
                        String url = json.getString("url");

                        client.setUrlPhoto(url);

                        return service.update(id, client)
                                .map(this::convertToDto)
                                .map(e -> ResponseEntity.ok().body(e));
                    } catch (Exception e) {
                        return Mono.error(new RuntimeException(e.getMessage()));
                    }
                });
    }

    @PostMapping("/v2/upload/{id}")
    public Mono<ResponseEntity<ClientDTO>> uploadV2(@PathVariable("id") String id, @RequestPart("file") FilePart filePart) throws IOException {
        File f = Files.createTempFile("temp", filePart.filename()).toFile();

        return filePart.transferTo(f)
                .then(service.findById(id).flatMap(client -> {
                    Map response;

                    try {
                        response = cloudinary.uploader().upload(f, ObjectUtils.asMap("resource_type", "auto"));
                        JSONObject json = new JSONObject(response);
                        String url = json.getString("url");

                        client.setUrlPhoto(url);

                        return service.update(id, client)
                                .map(this::convertToDto)
                                .map(e -> ResponseEntity.ok().body(e));

                    } catch (Exception e) {
                        return Mono.error(new RuntimeException());
                    }

                }));
    }

    @PostMapping("/v3/upload/{id}")
    public Mono<ResponseEntity<ClientDTO>> uploadV3(@PathVariable("id") String id, @RequestPart("file") FilePart filePart) throws Exception {
        return Mono.fromCallable(() -> {
                    //Crear un archivo temporal para almacenar lo recibido
                    return Files.createTempFile("temp", filePart.filename()).toFile();
                })
                .flatMap(tempFile -> filePart.transferTo(tempFile)
                        .then(service.findById(id)
                                .flatMap(client -> {
                                    return Mono.fromCallable(() -> {
                                                Map<String, Object> response = cloudinary.uploader().upload(tempFile, ObjectUtils.asMap("resource_type", "auto"));
                                                JSONObject json = new JSONObject(response);
                                                String url = json.getString("url");

                                                // Actualizar la URL de la foto del cliente
                                                client.setUrlPhoto(url);

                                                // Actualizar los datos del cliente en la base de datos
                                                return service.update(id, client)
                                                        .map(updatedClient -> ResponseEntity.ok().body(convertToDto(updatedClient)));
                                            })
                                            .flatMap(mono -> mono);
                                })
                        )
                );
    }


    private ClientDTO convertToDto(Client model) {
        return modelMapper.map(model, ClientDTO.class);
    }

    private Client convertToDocument(ClientDTO dto) {
        return modelMapper.map(dto, Client.class);
    }
}
