objectClass("User") {
    attribute("active") {
        jsonType "boolean";
        updateable true;
        description "Is user active";
    }
    attribute("email") {
        jsonType "string";
        openApiFormat "email";
        updateable true;
        description "The user's email address";
    }
    attribute("full_name") {
        jsonType "string";
        creatable true;
        updateable true;
        description "the user's full name";
    }
    attribute("id") {
        jsonType "integer";
        openApiFormat "int64";
        description "The unique identifier for the user";
    }
    attribute("login") {
        jsonType "string";
        description "the user's username";
    }

    connIdAttribute("UID", "id");
    connIdAttribute("NAME", "login");
}
