namespace my.catalog;

using {
    cuid,
    managed
} from '@sap/cds/common';

entity Categories : cuid, managed {
    name        : String(100);
    description : String(500);

    products    : Composition of many Products
                      on products.category = $self;
}

entity Products : cuid, managed {
    name        : String(200);
    description : String(1000);

    category    : Association to Categories;
}
