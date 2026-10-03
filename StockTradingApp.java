
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

// --- MAIN SERVER APPLICATION ---
public class StockTradingApp {

    private static final StockManager manager = new StockManager();

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

        server.createContext("/", new StaticFileHandler());
        server.createContext("/api/stocks", new StocksHandler());
        server.createContext("/api/portfolio", new PortfolioHandler());
        server.createContext("/api/trade", new TradeHandler());

        server.setExecutor(null);
        System.out.println("Stock Trading Server started successfully on http://localhost:8080");
        server.start();
    }

    static class StaticFileHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            File file = new File("index.html");
            if (!file.exists()) {
                String response = "404 (index.html not found in directory)";
                exchange.sendResponseHeaders(404, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
                return;
            }
            exchange.sendResponseHeaders(200, file.length());
            try (OutputStream os = exchange.getResponseBody()) {
                Files.copy(file.toPath(), os);
            }
        }
    }

    static class StocksHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);
            List<Stock> stocks = manager.getMarketStocks();
            StringBuilder json = new StringBuilder("[");
            for (int i = 0; i < stocks.size(); i++) {
                Stock s = stocks.get(i);
                json.append(String.format("{\"symbol\":\"%s\",\"name\":\"%s\",\"price\":%.2f,\"change\":%.2f}",
                        s.symbol, s.name, s.price, s.change));
                if (i < stocks.size() - 1) {
                    json.append(",");
                }
            }
            json.append("]");

            byte[] bytes = json.toString().getBytes("UTF-8");
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    static class PortfolioHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);
            Portfolio p = manager.getPortfolio();

            StringBuilder holdingsJson = new StringBuilder("[");
            int i = 0;
            for (Map.Entry<String, Integer> entry : p.holdings.entrySet()) {
                holdingsJson.append(String.format("{\"symbol\":\"%s\",\"shares\":%d}", entry.getKey(), entry.getValue()));
                if (++i < p.holdings.size()) {
                    holdingsJson.append(",");
                }
            }
            holdingsJson.append("]");

            StringBuilder txJson = new StringBuilder("[");
            for (int j = 0; j < p.transactions.size(); j++) {
                Transaction t = p.transactions.get(j);
                txJson.append(String.format("{\"type\":\"%s\",\"symbol\":\"%s\",\"shares\":%d,\"price\":%.2f,\"timestamp\":\"%s\"}",
                        t.type, t.symbol, t.shares, t.price, t.timestamp));
                if (j < p.transactions.size() - 1) {
                    txJson.append(",");
                }
            }
            txJson.append("]");

            String json = String.format("{\"cash\":%.2f, \"holdings\":%s, \"transactions\":%s}", p.cash, holdingsJson.toString(), txJson.toString());
            byte[] bytes = json.getBytes("UTF-8");
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    static class TradeHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);
            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            String jsonStr;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), "UTF-8"))) {
                StringBuilder body = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    body.append(line);
                }
                jsonStr = body.toString();
            }

            String action = extractString(jsonStr, "action");
            String symbol = extractString(jsonStr, "symbol");
            int shares = extractInt(jsonStr, "shares");

            String result = manager.executeTrade(action, symbol, shares);
            String response = "{\"status\":\"" + result + "\"}";
            byte[] bytes = response.getBytes("UTF-8");
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(result.equals("Success") ? 200 : 400, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    private static void setCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
    }

    private static int extractInt(String json, String key) {
        try {
            int idx = json.indexOf("\"" + key + "\":");
            if (idx == -1) {
                return 0;
            }
            int start = idx + key.length() + 3;
            int end = start;
            while (end < json.length() && Character.isDigit(json.charAt(end))) {
                end++;
            }
            return Integer.parseInt(json.substring(start, end).trim());
        } catch (NumberFormatException | StringIndexOutOfBoundsException e) {
            return 0;
        }
    }

    private static String extractString(String json, String key) {
        try {
            int idx = json.indexOf("\"" + key + "\":\"");
            if (idx == -1) {
                return "";
            }
            int start = idx + key.length() + 4;
            int end = json.indexOf("\"", start);
            return json.substring(start, end);
        } catch (StringIndexOutOfBoundsException e) {
            return "";
        }
    }
}

// --- OOP MODELS & MANAGER ---
class Stock {

    String symbol;
    String name;
    double price;
    double change;

    public Stock(String symbol, String name, double price, double change) {
        this.symbol = symbol;
        this.name = name;
        this.price = price;
        this.change = change;
    }
}

class Transaction {

    String type;
    String symbol;
    int shares;
    double price;
    String timestamp;

    public Transaction(String type, String symbol, int shares, double price, String timestamp) {
        this.type = type;
        this.symbol = symbol;
        this.shares = shares;
        this.price = price;
        this.timestamp = timestamp;
    }
}

class Portfolio {

    double cash;
    Map<String, Integer> holdings;
    List<Transaction> transactions;

    public Portfolio(double cash) {
        this.cash = cash;
        this.holdings = new HashMap<>();
        this.transactions = new ArrayList<>();
    }
}

class StockManager {

    private final String dataFilePath = "stock_data.json";
    private List<Stock> marketStocks;
    private Portfolio portfolio;

    public StockManager() {
        initMarket();
        loadData(); // Safe now since loadData is private/final
    }

    private void initMarket() {
        marketStocks = new ArrayList<>();
        marketStocks.add(new Stock("AAPL", "Apple Inc.", 185.50, +1.20));
        marketStocks.add(new Stock("TSLA", "Tesla Inc.", 240.25, -2.45));
        marketStocks.add(new Stock("GOOGL", "Alphabet Inc.", 142.10, +0.85));
        marketStocks.add(new Stock("AMZN", "Amazon.com Inc.", 175.80, +1.50));
        marketStocks.add(new Stock("MSFT", "Microsoft Corp.", 415.30, +0.40));
    }

    private final synchronized void loadData() {
        File file = new File(dataFilePath);
        if (!file.exists()) {
            portfolio = new Portfolio(10000.0);
            saveData();
        } else {
            try {
                Files.readAllBytes(Paths.get(dataFilePath));
                portfolio = new Portfolio(10000.0);
            } catch (IOException e) {
                portfolio = new Portfolio(10000.0);
            }
        }
    }

    public synchronized void saveData() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("{\"cash\":%.2f, \"transactionsCount\":%d}", portfolio.cash, portfolio.transactions.size()));
        try (FileWriter file = new FileWriter(dataFilePath)) {
            file.write(sb.toString());
        } catch (IOException e) {
            System.err.println("Error saving data: " + e.getMessage());
        }
    }

    public List<Stock> getMarketStocks() {
        Random rand = new Random();
        for (Stock s : marketStocks) {
            double fluctuation = (rand.nextDouble() - 0.48) * 2.0;
            s.price = Math.round((s.price + fluctuation) * 100.0) / 100.0;
            s.change = Math.round((s.change + (fluctuation / 10)) * 100.0) / 100.0;
        }
        return marketStocks;
    }

    public Portfolio getPortfolio() {
        return portfolio;
    }

    public synchronized String executeTrade(String action, String symbol, int shares) {
        if (shares <= 0) {
            return "Invalid share count";
        }

        Stock targetStock = null;
        for (Stock s : marketStocks) {
            if (s.symbol.equalsIgnoreCase(symbol)) {
                targetStock = s;
                break;
            }
        }

        if (targetStock == null) {
            return "Stock not found";
        }

        double totalCost = targetStock.price * shares;

        if (action.equalsIgnoreCase("BUY")) {
            if (portfolio.cash < totalCost) {
                return "Insufficient funds";
            }
            portfolio.cash -= totalCost;
            portfolio.holdings.put(symbol, portfolio.holdings.getOrDefault(symbol, 0) + shares);
        } else if (action.equalsIgnoreCase("SELL")) {
            int currentShares = portfolio.holdings.getOrDefault(symbol, 0);
            if (currentShares < shares) {
                return "Insufficient shares owned";
            }
            portfolio.cash += totalCost;
            portfolio.holdings.put(symbol, currentShares - shares);
            if (portfolio.holdings.get(symbol) == 0) {
                portfolio.holdings.remove(symbol);
            }
        } else {
            return "Invalid action";
        }

        portfolio.transactions.add(0, new Transaction(action.toUpperCase(), symbol, shares, targetStock.price, new Date().toString()));
        saveData();
        return "Success";
    }
}
