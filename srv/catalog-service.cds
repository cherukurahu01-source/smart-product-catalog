using {my.catalog as db} from '../db/schema';
@mcp
@agent
service CatalogService {
    entity Products   as
        projection on db.Products {
            *
        }
        excluding {
            embedding
        };

    entity Categories as projection on db.Categories;

    function semanticSearch(query: String,
                            topK: Integer)        returns array of Products;

    function similarProducts(productId: String,
                             topK: Integer)       returns array of Products;

    function recommendProducts(productId: String,
                               topK: Integer)     returns array of Products;

    function askProductQuestion(question: String) returns String;
    function classifyProduct(description: String) returns String;
}
