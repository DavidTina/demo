databaseChangeLog = {

    changeSet(author: "jun.zhang", id: "creat table demo") {
        preConditions(onFail: "MARK_RAN") {
            not {
                tableExists(tableName: "demo")
            }
        }

        createTable(tableName: "demo") {
            column(autoIncrement: "true", name: "id", type: "BIGINT") {
                constraints(primaryKey: "true")
            }
            column(name: "version", type: "BIGINT") {
                constraints(nullable: "false")
            }
            column(name: "name", type: "VARCHAR(128)")
            column(name: "date_created", type: "datetime") {
                constraints(nullable: "false")
            }
            column(name: "last_updated", type: "datetime") {
                constraints(nullable: "false")
            }
        }
    }

}

