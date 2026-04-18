# MapStruct — Mapeamento Automático Entity ↔ DTO

## O que é MapStruct?

### Explicação não técnica

Imagine que você tem um formulário em papel (Entity) com todos os dados de uma conta bancária: ID, nome, saldo, usuário dono, data de criação, data de atualização, e se está ativa. Mas quando vai mostrar esses dados para alguém, não precisa de tudo — só ID, nome e saldo. Então você copia manualmente os 3 campos relevantes para um cartão resumo (DTO).

MapStruct é como ter um **assistente que faz essa cópia automaticamente**. Você só diz "quero um cartão resumo a partir deste formulário" e ele cria a regra de cópia sozinho, sem errar nenhum campo.

### Explicação técnica

MapStruct é um **processador de anotações Java** (annotation processor) que gera código de mapeamento em **tempo de compilação**. Diferente de bibliotecas como ModelMapper ou Dozer que usam reflection em runtime, MapStruct gera classes concretas `.java` durante o `mvn compile`, resultando em:

- **Zero overhead de performance** (não usa reflection)
- **Erros detectados em compilação** (não em runtime)
- **Código gerado legível** (pode ser inspecionado em `target/generated-sources/`)

---

## Por que foi adotado?

Antes do MapStruct, cada DTO de resposta tinha um construtor manual que recebia a Entity e copiava campo a campo:

### Antes — Conversão manual (código real removido)

```java
// Exemplo do que existia antes no ContaResponseDTO
public record ContaResponseDTO(Long id, String nome, BigDecimal saldo) {
    // Construtor manual que recebia a entity
    public ContaResponseDTO(ContaEntity entity) {
        this(entity.getId(), entity.getNome(), entity.getSaldo());
    }
}
```

Problemas:
- Cada DTO precisava de um construtor de conversão
- Se a entity ganhasse um novo campo, era necessário atualizar o construtor manualmente
- Código repetitivo em 7+ DTOs

### Depois — MapStruct (código real do projeto)

```java
// ContaMapper.java
@Mapper(componentModel = "spring")
public interface ContaMapper {
    ContaResponseDTO toResponse(ContaEntity entity);
}
```

```java
// ContaResponseDTO.java — sem construtor de conversão
/** DTO de resposta com os dados de uma conta bancária do usuário. */
public record ContaResponseDTO(
        Long id,
        String nome,
        BigDecimal saldo
) {}
```

O MapStruct gera automaticamente a implementação do `toResponse()` com todos os getters/setters mapeados por nome.

---

## Os 8 Mappers do Projeto

| Mapper | Entity → DTO |
|---|---|
| `ContaMapper` | `ContaEntity` → `ContaResponseDTO` |
| `CartaoMapper` | `CartaoEntity` → `CartaoResponseDTO` |
| `CategoriaMapper` | `CategoriaEntity` → `CategoriaResponseDTO` |
| `TransacaoMapper` | `TransacaoEntity` → `TransacaoResponseDTO` |
| `TransacaoRecorrenteMapper` | `TransacaoRecorrenteEntity` → `TransacaoRecorrenteResponseDTO` |
| `FaturaMapper` | `FaturaEntity` → `FaturaResponseDTO` |
| `InvestimentoMapper` | `InvestimentoEntity` → `InvestimentoResponseDTO` |
| `UsuarioMapper` | `UsuarioEntity` → `UsuarioResponseDTO` |

Todos seguem o mesmo padrão: uma interface com `@Mapper(componentModel = "spring")` e um método `toResponse()`.

---

## Configuração no pom.xml

Para MapStruct funcionar com Lombok (que também é um annotation processor), a ordem de execução precisa ser configurada:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <annotationProcessorPaths>
            <!-- Lombok DEVE vir ANTES do MapStruct -->
            <path>
                <groupId>org.projectlombok</groupId>
                <artifactId>lombok</artifactId>
            </path>
            <!-- Binding Lombok+MapStruct -->
            <path>
                <groupId>org.projectlombok</groupId>
                <artifactId>lombok-mapstruct-binding</artifactId>
                <version>0.2.0</version>
            </path>
            <path>
                <groupId>org.mapstruct</groupId>
                <artifactId>mapstruct-processor</artifactId>
            </path>
        </annotationProcessorPaths>
    </configuration>
</plugin>
```

**Por que a ordem importa?** Lombok gera getters/setters durante a compilação. MapStruct precisa desses getters para gerar o código de mapeamento. Se MapStruct executar antes do Lombok, ele não encontra os getters e falha.

---

## Como é usado nos Services

```java
// ContaService.java — trecho real
public ContaResponseDTO criarConta(ContaRegistroRequestDTO dto, Long usuarioId) {
    // ... criação da entity ...
    contaRepository.save(conta);
    return contaMapper.toResponse(conta); // MapStruct faz a conversão
}
```

O `contaMapper` é injetado automaticamente pelo Spring (graças ao `componentModel = "spring"`), como qualquer outro bean.

---

## Fontes

- [MapStruct — Documentação Oficial](https://mapstruct.org/documentation/stable/reference/html/)
- [MapStruct + Lombok — Guia de Integração](https://mapstruct.org/documentation/stable/reference/html/#lombok)
- [Baeldung — Introduction to MapStruct](https://www.baeldung.com/mapstruct)
- [Maven Annotation Processor Paths](https://maven.apache.org/plugins/maven-compiler-plugin/compile-mojo.html#annotationProcessorPaths)
