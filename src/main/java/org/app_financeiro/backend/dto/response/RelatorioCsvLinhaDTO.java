package org.app_financeiro.backend.dto.response;

import com.opencsv.bean.CsvBindByName;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class RelatorioCsvLinhaDTO {

    @CsvBindByName(column = "Data da Transação", required = true)
    private String data;

    @CsvBindByName(column = "Tipo", required = true)
    private String tipo;

    @CsvBindByName(column = "Descrição", required = true)
    private String descricao;

    @CsvBindByName(column = "Categoria", required = true)
    private String descricaoCategoria;

    @CsvBindByName(column = "Banco / Cartão")
    private String contaOuCartao;

    @CsvBindByName(column = "Valor Origem (R$)", required = true)
    private String valor;

    @CsvBindByName(column = "Status")
    private String status;
}
