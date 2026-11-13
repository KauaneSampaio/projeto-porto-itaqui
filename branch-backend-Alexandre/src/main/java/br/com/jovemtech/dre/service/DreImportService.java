package br.com.jovemtech.dre.service;

import br.com.jovemtech.dre.model.*;
import br.com.jovemtech.dre.repository.DreRepository;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DreImportService {
    private static final String HEADER="conta_id;conta_pai_id;descricao;grupo;ano;mes;perspectiva;valor;natureza";
    private final DreRepository repository;
    public DreImportService(DreRepository repository){this.repository=repository;}
    public int importar(MultipartFile arquivo) throws IOException {
        return importar(arquivo,false);
    }
    public int importar(MultipartFile arquivo, boolean substituirBase) throws IOException {
        if(arquivo.isEmpty())throw new ImportacaoException(List.of("Arquivo vazio"));
        List<DreRegistro> validos=new ArrayList<>();
        List<String> erros=new ArrayList<>();
        try(BufferedReader reader=new BufferedReader(new InputStreamReader(arquivo.getInputStream(),StandardCharsets.UTF_8))) {
            String header=reader.readLine();
            if(header==null||!header.replace("\uFEFF", "").trim().equals(HEADER))
                throw new ImportacaoException(List.of("Cabeçalho inválido; esperado: "+HEADER));
            String linha;int numero=1;
            while((linha=reader.readLine())!=null) {
                numero++;
                if(linha.isBlank())continue;
                String[] c=linha.split(";",-1);
                if(c.length!=9){erros.add("Linha "+numero+": esperado 9 colunas");continue;}
                try {
                    String conta=obrigatorio(c[0],"conta_id");
                    String descricao=obrigatorio(c[2],"descricao");
                    String grupo=obrigatorio(c[3],"grupo").toUpperCase(Locale.ROOT);
                    int ano=Integer.parseInt(c[4].trim()),mes=Integer.parseInt(c[5].trim());
                    if(ano<2000||ano>2100||mes<1||mes>12)
                        throw new IllegalArgumentException("ano ou mês fora do intervalo");
                    Perspectiva perspectiva=Perspectiva.valueOf(c[6].trim().toUpperCase(Locale.ROOT));
                    BigDecimal valor=new BigDecimal(c[7].trim());
                    NaturezaDado natureza=NaturezaDado.valueOf(c[8].trim().toUpperCase(Locale.ROOT));
                    validos.add(new DreRegistro(conta,c[1].trim().isEmpty()?null:c[1].trim(),
                        descricao,grupo,ano,mes,perspectiva,valor,natureza));
                } catch(IllegalArgumentException ex) {erros.add("Linha "+numero+": "+ex.getMessage());}
            }
        }
        if(!erros.isEmpty())throw new ImportacaoException(erros);
        if(validos.isEmpty())throw new ImportacaoException(List.of("Nenhum registro encontrado"));
        if(substituirBase) repository.substituirTodos(validos);
        else repository.salvarTodos(validos);
        return validos.size();
    }
    private static String obrigatorio(String valor,String campo) {
        if(valor.isBlank())throw new IllegalArgumentException(campo+" obrigatório");
        return valor.trim();
    }
    public List<DreRegistro> listar(){return repository.listar();}
}
