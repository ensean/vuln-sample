package demo;

import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

@Controller
public class VulnController {

    private final DataSource ds;

    public VulnController(DataSource ds) {
        this.ds = ds;
        initDb();
    }

    private void initDb() {
        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS users(id INT PRIMARY KEY, username VARCHAR(255), password VARCHAR(255), role VARCHAR(50))");
            s.execute("MERGE INTO users VALUES(1,'admin','admin123','admin')");
            s.execute("MERGE INTO users VALUES(2,'user1','password1','user')");
            s.execute("MERGE INTO users VALUES(3,'user2','password2','user')");
            s.execute("CREATE TABLE IF NOT EXISTS notes(id INT AUTO_INCREMENT PRIMARY KEY, user_id INT, content VARCHAR(1000))");
            s.execute("MERGE INTO notes(id,user_id,content) KEY(id) VALUES(1,1,'Admin secret note: DB password is P@ssw0rd!')");
            s.execute("MERGE INTO notes(id,user_id,content) KEY(id) VALUES(2,2,'User1 personal note')");
        } catch (Exception e) { e.printStackTrace(); }
    }

    @GetMapping("/")
    public String index() { return "index"; }

    // --- Vuln 1: SQL Injection ---
    @GetMapping("/search")
    public String search(@RequestParam(defaultValue = "") String q, Model model) {
        List<Map<String, String>> results = new ArrayList<>();
        try (Connection c = ds.getConnection();
             Statement s = c.createStatement();
             // VULNERABLE: direct string concatenation in SQL
             ResultSet rs = s.executeQuery("SELECT id, username, role FROM users WHERE username LIKE '%" + q + "%'")) {
            while (rs.next()) {
                Map<String, String> row = new HashMap<>();
                row.put("id", rs.getString("id"));
                row.put("username", rs.getString("username"));
                row.put("role", rs.getString("role"));
                results.add(row);
            }
        } catch (Exception e) { model.addAttribute("error", e.getMessage()); }
        model.addAttribute("results", results);
        model.addAttribute("q", q);
        return "search";
    }

    // --- Vuln 2: Reflected XSS ---
    @GetMapping("/greet")
    @ResponseBody
    public String greet(@RequestParam(defaultValue = "World") String name) {
        // VULNERABLE: user input directly in HTML without escaping
        return "<html><body><h1>Hello, " + name + "!</h1><a href='/'>Back</a></body></html>";
    }

    // --- Vuln 3: Path Traversal ---
    @GetMapping("/file")
    @ResponseBody
    public String readFile(@RequestParam String name) {
        try {
            // VULNERABLE: no path validation
            return Files.readString(Path.of("/tmp/" + name));
        } catch (Exception e) { return "Error: " + e.getMessage(); }
    }

    // --- Vuln 4: Command Injection ---
    @GetMapping("/ping")
    @ResponseBody
    public String ping(@RequestParam String host) {
        try {
            // VULNERABLE: user input passed directly to shell
            Process p = Runtime.getRuntime().exec(new String[]{"/bin/sh", "-c", "ping -c 1 " + host});
            return new String(p.getInputStream().readAllBytes());
        } catch (Exception e) { return "Error: " + e.getMessage(); }
    }

    // --- Vuln 5: SSRF ---
    @GetMapping("/fetch")
    @ResponseBody
    public String fetch(@RequestParam String url) {
        try {
            // VULNERABLE: no URL validation, can access internal metadata etc.
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            return new String(conn.getInputStream().readAllBytes());
        } catch (Exception e) { return "Error: " + e.getMessage(); }
    }

    // --- Vuln 6: IDOR (Insecure Direct Object Reference) ---
    @GetMapping("/note/{id}")
    @ResponseBody
    public String getNote(@PathVariable int id) {
        // VULNERABLE: no authorization check, any user can read any note
        try (Connection c = ds.getConnection();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT content FROM notes WHERE id=" + id)) {
            if (rs.next()) return rs.getString("content");
            return "Not found";
        } catch (Exception e) { return "Error: " + e.getMessage(); }
    }

    // --- Vuln 7: Sensitive Data Exposure ---
    @GetMapping("/debug")
    @ResponseBody
    public Map<String, String> debug() {
        // VULNERABLE: exposes environment variables and system properties
        Map<String, String> info = new HashMap<>();
        info.put("java.version", System.getProperty("java.version"));
        info.put("os.name", System.getProperty("os.name"));
        info.put("user.dir", System.getProperty("user.dir"));
        info.put("DB_PASSWORD", "P@ssw0rd!");
        info.put("API_KEY", "DEMOKEYfsu0u23rfsa2r324");
        return info;
    }
}
