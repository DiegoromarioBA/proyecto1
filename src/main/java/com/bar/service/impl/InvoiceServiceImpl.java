package com.bar.service.impl;

import com.bar.model.Invoice;
import com.bar.model.InvoiceDetail;
import com.bar.repo.IClientRepo;
import com.bar.repo.IDishRepo;
import com.bar.repo.IInvoiceRepo;
import com.bar.repo.IGenericRepo;
import com.bar.service.IInvoiceService;
import lombok.RequiredArgsConstructor;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class InvoiceServiceImpl extends CRUDImpl<Invoice, String> implements IInvoiceService {

    private final IInvoiceRepo invoiceRepo;
    private final IClientRepo clientRepo;
    private final IDishRepo dishRepo;

    @Override
    protected IGenericRepo<Invoice, String> getRepo() {
        return invoiceRepo;
    }

    private Mono<Invoice> populateClient(Invoice invoice) {
        return clientRepo.findById(invoice.getClient().getId())
                .map(client -> {
                    invoice.setClient(client);
                    return invoice;
                });
    }

    private Mono<Invoice> populateItems(Invoice invoice){
        List<Mono<InvoiceDetail>> list = invoice.getItems().stream()
                .map(item -> dishRepo.findById(item.getDish().getId())
                        .map(dish -> {
                            item.setDish(dish);
                            return item;
                        })
                ).toList();

        return Mono.when(list).then(Mono.just(invoice));
    }


    private byte[] generatePDF(Invoice invoice){
        try (InputStream stream = getClass().getResourceAsStream("/facturas.jrxml")) {
            Map<String, Object> parameters = new HashMap<>();
            parameters.put("txt_client", invoice.getClient().getFirstName());

            JasperReport report = JasperCompileManager.compileReport(stream);
            JasperPrint print = JasperFillManager.fillReport(report, parameters, new JRBeanCollectionDataSource(invoice.getItems()));
            return JasperExportManager.exportReportToPdf(print);
        } catch (Exception e) {
            return new byte[0];
        }
    }


    @Override
    public Mono<byte[]> generateReport(String idInvoice) {
        return invoiceRepo.findById(idInvoice)
                .flatMap(this::populateClient)
                .flatMap(this::populateItems)
                .map(this::generatePDF)
                .onErrorResume(e -> Mono.empty());
    }


    /*@Override
    public Mono<byte[]> generateReport(String idInvoice) {
        return invoiceRepo.findById(idInvoice)
                //Obteniendo Client
                .flatMap(inv -> Mono.just(inv)
                        .zipWith(clientRepo.findById(inv.getClient().getId()), (in, cl) -> {
                            in.setClient(cl);
                            return in;
                        })
                )
                //Obteniendo cada Dish
                .flatMap(inv -> {
                    return Flux.fromIterable(inv.getItems())
                            .flatMap(item -> {
                                return dishRepo.findById(item.getDish().getId())
                                        .map(d -> {
                                            item.setDish(d);
                                            return item;
                                        });
                            }).collectList()
                            .flatMap(list -> {
                                inv.setItems(list);
                                return Mono.just(inv);
                            });
                })
                .map(inv -> {
                    try {
                        Map<String, Object> parameters = new HashMap<>();
                        parameters.put("txt_client", inv.getClient().getFirstName() + " " + inv.getClient().getLastName());

                        InputStream stream = getClass().getResourceAsStream("/facturas.jrxml");
                        JasperReport jasper = JasperCompileManager.compileReport(stream);
                        JasperPrint print = JasperFillManager.fillReport(jasper, parameters, new JRBeanCollectionDataSource(inv.getItems()));
                        return JasperExportManager.exportReportToPdf(print);
                    }
                    catch(Exception e){
                        return new byte[0];
                    }
                });
    }*/
}
