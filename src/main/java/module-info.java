open module application.giaodien {
    /* ────────── JavaFX ────────── */
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;
    requires javafx.swing;

    /* ────────── UI libs ────────── */
    requires org.kordamp.ikonli.core;
    requires org.kordamp.ikonli.javafx;

    /* ────────── Hibernate / JPA ────────── */
    requires org.hibernate.orm.core;
    requires jakarta.persistence;
    requires jakarta.transaction;
    requires jakarta.inject;          // đã thêm API Inject trong POM
    requires static jakarta.cdi;

    /* ────────── Gửi mail ────────── */
    requires jakarta.mail;            // ★ mới

    /* ────────── Spring Framework ────────── */
    requires spring.context;
    requires spring.core;
    requires spring.beans;
    requires spring.aop;
    requires spring.data.jpa;
    requires spring.data.commons;
    requires spring.jdbc;
    requires spring.orm;
    requires spring.tx;

    /* ────────── Khác ────────── */
    requires jbcrypt;
    requires mysql.connector.j;
    requires com.google.gson;
    requires com.gluonhq.emoji;
    requires com.gluonhq.emoji.offline;
    requires com.fasterxml.jackson.annotation;
    requires java.desktop;
    requires net.coobird.thumbnailator;

    /* ================== EXPORT ================== */
    exports application.giaodien;
    exports app;

    exports app.controller;

    /* ================== OPENS =================== */
//    opens application.giaodien to javafx.graphics, javafx.fxml;
//    opens app.controller       to javafx.fxml;
//    opens app.model            to org.hibernate.orm.core, com.google.gson, javafx.base;
//    opens app.service          to org.hibernate.orm.core;
}
