using {my.catalog as db} from '../db/schema';

service CatalogService {

    entity Products   as projection on db.Products;
    entity Categories as projection on db.Categories;

}
