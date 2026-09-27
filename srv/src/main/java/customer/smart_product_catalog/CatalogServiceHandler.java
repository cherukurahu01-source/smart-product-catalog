package customer.smart_product_catalog;

import java.util.List;

import org.springframework.stereotype.Component;

import com.sap.cds.ql.CQL;
import com.sap.cds.ql.Select;
import com.sap.cds.ql.cqn.CqnSelect;
import com.sap.cds.ql.cqn.CqnVector;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.On;
import com.sap.cds.services.handler.annotations.ServiceName;
import com.sap.cds.services.persistence.PersistenceService;

import cds.gen.catalogservice.Products;
import cds.gen.catalogservice.SemanticSearchContext;
import cds.gen.catalogservice.AskProductQuestionContext;
import cds.gen.catalogservice.SimilarProductsContext;
import cds.gen.catalogservice.RecommendProductsContext;
import cds.gen.my.catalog.Products_;

import dev.langchain4j.model.chat.ChatModel;
import com.sap.cds.services.ai.langchain4j.Langchain4jChatProvider;
import com.sap.cds.services.runtime.CdsRuntime;
import cds.gen.catalogservice.ClassifyProductContext;
@Component
@ServiceName("CatalogService")
public class CatalogServiceHandler implements EventHandler {

private final PersistenceService db;
private final CdsRuntime runtime;

public CatalogServiceHandler(
        PersistenceService db,
        CdsRuntime runtime) {

    this.db = db;
    this.runtime = runtime;
}
    @On(event = "semanticSearch")
    public void semanticSearch(SemanticSearchContext context) {

        // Read function parameters
        String query = (String) context.get("query");
        Integer topK = (Integer) context.get("topK");

        System.out.println("Search query: " + query);
        System.out.println("Top K: " + topK);

        // Create an embedding for the user's search query
        var queryEmbedding = CQL.vectorEmbedding(
                CQL.val(query),
                CqnVector.TextType.QUERY,
                "SAP_GXY.20250407"
        );

        // Perform cosine similarity search
        CqnSelect select = Select.from(Products_.class)
                .where(p -> CQL.cosineSimilarity(
                        p.embedding(),
                        queryEmbedding
                ).gt(0.1))
                .limit(topK);

        // Execute query against the database
        List<cds.gen.my.catalog.Products> dbProducts =
                db.run(select).listOf(cds.gen.my.catalog.Products.class);

        System.out.println("Products found: " + dbProducts.size());

        // Convert database Products to service Products
        List<Products> results = dbProducts.stream()
                .map(p -> {
                    Products result = Products.create();

                    result.setId(p.getId());
                    result.setName(p.getName());
                    result.setDescription(p.getDescription());

                    if (p.getCategoryId() != null) {
                        result.setCategoryId(p.getCategoryId());
                    }

                    return result;
                })
                .toList();

        // Return the semantic-search results
        context.setResult(results);
    }

@On(event = "similarProducts")
public void similarProducts(SimilarProductsContext context) {

    String productId = (String) context.get("productId");
    Integer topK = (Integer) context.get("topK");

    System.out.println("Product ID: " + productId);
    System.out.println("Top K: " + topK);

var productOptional = db.run(
    Select.from(Products_.class)
        .columns(
            p -> p.ID(),
            p -> p.name(),
            p -> p.description(),
            p -> p.embedding()
        )
        .where(p -> p.ID().eq(productId))
).first(cds.gen.my.catalog.Products.class);

    if (productOptional.isEmpty()) {
        context.setResult(List.of());
        return;
    }

    var product = productOptional.get();

    var productEmbedding = product.getEmbedding();

    System.out.println( "Vector value : " + productEmbedding);
    System.out.println(product);

    CqnSelect select = Select.from(Products_.class)
        .where(p -> CQL.cosineSimilarity(
            p.embedding(),
             CQL.vector(productEmbedding)
        ).gt(0.5))
        .limit(topK);

    List<cds.gen.my.catalog.Products> dbProducts =
        db.run(select)
          .listOf(cds.gen.my.catalog.Products.class);

    List<Products> results = dbProducts.stream()
        .filter(p -> !productId.equals(p.getId()))
        .map(p -> {
            Products result = Products.create();

            result.setId(p.getId());
            result.setName(p.getName());
            result.setDescription(p.getDescription());

            if (p.getCategoryId() != null) {
                result.setCategoryId(p.getCategoryId());
            }

            return result;
        })
        .toList();

    context.setResult(results);
}

@On(event = "recommendProducts")
public void recommendProducts(RecommendProductsContext context) {

    String productId = (String) context.get("productId");
    Integer topK = (Integer) context.get("topK");

    System.out.println("Recommendation product ID: " + productId);
    System.out.println("Recommendation Top K: " + topK);

    // 1. Find the selected product and retrieve its embedding
    var productOptional = db.run(
        Select.from(Products_.class)
            .columns(
                p -> p.ID(),
                p -> p.name(),
                p -> p.description(),
                p -> p.embedding()
            )
            .where(p -> p.ID().eq(productId))
    ).first(cds.gen.my.catalog.Products.class);

    // Product doesn't exist
    if (productOptional.isEmpty()) {
        context.setResult(List.of());
        return;
    }

    var product = productOptional.get();
    var productEmbedding = product.getEmbedding();

    // 2. Find products with similar embeddings
    CqnSelect select = Select.from(Products_.class)
        .columns(
            p -> p.ID(),
            p -> p.name(),
            p -> p.description(),
            p -> p.category_ID()
        )
        .where(p -> CQL.cosineSimilarity(
            p.embedding(),
            CQL.vector(productEmbedding)
        ).gt(0.0))
        .limit(topK + 1);

    List<cds.gen.my.catalog.Products> dbProducts =
        db.run(select)
          .listOf(cds.gen.my.catalog.Products.class);

    // 3. Remove the product being used as the recommendation source
    List<Products> results = dbProducts.stream()
        .filter(p -> !productId.equals(p.getId()))
        .limit(topK)
        .map(p -> {
            Products result = Products.create();

            result.setId(p.getId());
            result.setName(p.getName());
            result.setDescription(p.getDescription());

            if (p.getCategoryId() != null) {
                result.setCategoryId(p.getCategoryId());
            }

            return result;
        })
        .toList();

    // 4. Return recommendations
    context.setResult(results);
}
@On(event = "askProductQuestion")
public void askProductQuestion(AskProductQuestionContext context) {

    String question = (String) context.get("question");

    System.out.println("RAG question: " + question);

    // 1. Convert the question into an embedding
    var queryEmbedding = CQL.vectorEmbedding(
            CQL.val(question),
            CqnVector.TextType.QUERY,
            "SAP_GXY.20250407"
    );

    // 2. Retrieve relevant products from HANA
    CqnSelect select = Select.from(Products_.class)
            .where(p -> CQL.cosineSimilarity(
                    p.embedding(),
                    queryEmbedding
            ).gt(0.1))
            .limit(3);

    List<cds.gen.my.catalog.Products> products =
            db.run(select)
              .listOf(cds.gen.my.catalog.Products.class);

    System.out.println("Retrieved products: " + products.size());

    // 3. Build context for the LLM
    StringBuilder productContext = new StringBuilder();

    for (var product : products) {
        productContext
                .append("Product: ")
                .append(product.getName())
                .append("\nDescription: ")
                .append(product.getDescription())
                .append("\n\n");
    }


    // 4. Create the RAG prompt
    String promptText = """
            Answer the user's question using only the product
            information provided below.

            Product information:
            %s

            User question:
            %s

            If the answer cannot be found in the product information,
            say that the information is not available.
            """.formatted(productContext, question);

            System.out.println("RAG context:\n" + productContext);
            System.out.println("RAG prompt:\n" + promptText);

Langchain4jChatProvider provider =
        Langchain4jChatProvider.of("llm", runtime);

ChatModel chatModel = provider.chatModel();

String answer = chatModel.chat(promptText);

context.setResult(answer);
}

@On(event = "classifyProduct")
public void classifyProduct(ClassifyProductContext context) {

    String description = (String) context.get("description");

    System.out.println("Classification input: " + description);

    // Temporary implementation
    String category;

    if (description.toLowerCase().contains("laptop")
            || description.toLowerCase().contains("computer")) {
        category = "Electronics";
    } else if (description.toLowerCase().contains("headphone")
            || description.toLowerCase().contains("audio")) {
        category = "Audio";
    } else if (description.toLowerCase().contains("phone")
            || description.toLowerCase().contains("smartphone")) {
        category = "Mobile";
    } else {
        category = "Other";
    }

    context.setResult(category);
}
}