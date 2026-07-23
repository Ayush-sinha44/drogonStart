package com.ayush.drogonStart.registry;

import com.ayush.drogonStart.dto.DependencyDefinition;
import com.ayush.drogonStart.model.DependencyCategory;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * In-memory catalog of all available dependencies for Drogon project scaffolding.
 *
 * Dependencies are split into two integration strategies:
 * - Built-in: Drogon-native features (databases, Redis) configured via config.json
 * - External: Third-party C++ libraries fetched via CMake FetchContent
 */
@Component
@Slf4j
public class DependencyRegistry {

    private final Map<String, DependencyDefinition> dependencies = new LinkedHashMap<>();

    @PostConstruct
    public void init() {
        registerBuiltInDependencies();
        registerExternalDependencies();
        log.info("Dependency registry initialized with {} dependencies", dependencies.size());
    }

    // ==================== PUBLIC API ====================

    /**
     * Returns all registered dependencies.
     */
    public List<DependencyDefinition> getAll() {
        return new ArrayList<>(dependencies.values());
    }

    /**
     * Returns all dependencies grouped by category.
     */
    public Map<String, List<DependencyDefinition>> getAllGroupedByCategory() {
        return dependencies.values().stream()
                .collect(Collectors.groupingBy(
                        dep -> dep.getCategory().name(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
    }

    /**
     * Returns a single dependency by ID, or empty if not found.
     */
    public Optional<DependencyDefinition> getById(String id) {
        return Optional.ofNullable(dependencies.get(id));
    }

    /**
     * Returns dependency definitions for the given IDs.
     * Silently skips unknown IDs.
     */
    public List<DependencyDefinition> getByIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return ids.stream()
                .map(dependencies::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Validates that all provided dependency IDs exist in the registry.
     *
     * @return list of invalid IDs (empty if all are valid)
     */
    public List<String> validateIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return ids.stream()
                .filter(id -> !dependencies.containsKey(id))
                .collect(Collectors.toList());
    }

    // ==================== BUILT-IN DEPENDENCIES ====================

    private void registerBuiltInDependencies() {

        // --- PostgreSQL ---
        register(DependencyDefinition.builder()
                .id("postgresql")
                .name("PostgreSQL Driver")
                .description("PostgreSQL database support via Drogon's built-in async ORM. " +
                        "Requires libpq on the build system.")
                .category(DependencyCategory.DATABASE)
                .builtIn(true)
                .configJsonKey("db_clients")
                .configJsonSnippet("""
                        {
                            "name": "default",
                            "rdbms": "postgresql",
                            "host": "127.0.0.1",
                            "port": 5432,
                            "dbname": "your_database",
                            "user": "postgres",
                            "passwd": "",
                            "is_fast": false,
                            "number_of_connections": 4,
                            "timeout": 10.0
                        }""")
                .requiredSystemPackages(List.of("libpq-dev"))
                .exampleFileName("ExampleDbController.cc")
                .exampleCode("""
                        #include <drogon/HttpController.h>
                        #include <drogon/orm/DbClient.h>

                        using namespace drogon;
                        using namespace drogon::orm;

                        /**
                         * Example controller demonstrating PostgreSQL database access.
                         *
                         * Prerequisites:
                         *   1. Install libpq-dev: sudo apt install libpq-dev
                         *   2. Rebuild Drogon with PostgreSQL support
                         *   3. Configure db_clients in config.json
                         *   4. Create your database and tables
                         */
                        class ExampleDbController : public HttpController<ExampleDbController>
                        {
                        public:
                            METHOD_LIST_BEGIN
                            ADD_METHOD_TO(ExampleDbController::getUsers, "/api/users", Get);
                            METHOD_LIST_END

                            void getUsers(const HttpRequestPtr &req,
                                          std::function<void(const HttpResponsePtr &)> &&callback)
                            {
                                auto dbClient = app().getDbClient("default");

                                dbClient->execSqlAsync(
                                    "SELECT id, name, email FROM users LIMIT 100",
                                    [callback](const Result &result) {
                                        Json::Value ret(Json::arrayValue);
                                        for (const auto &row : result) {
                                            Json::Value user;
                                            user["id"] = row["id"].as<int>();
                                            user["name"] = row["name"].as<std::string>();
                                            user["email"] = row["email"].as<std::string>();
                                            ret.append(user);
                                        }
                                        auto resp = HttpResponse::newHttpJsonResponse(ret);
                                        callback(resp);
                                    },
                                    [callback](const DrogonDbException &e) {
                                        Json::Value err;
                                        err["error"] = e.base().what();
                                        auto resp = HttpResponse::newHttpJsonResponse(err);
                                        resp->setStatusCode(k500InternalServerError);
                                        callback(resp);
                                    });
                            }
                        };
                        """)
                .build());

        // --- MySQL / MariaDB ---
        register(DependencyDefinition.builder()
                .id("mysql")
                .name("MySQL/MariaDB Driver")
                .description("MySQL or MariaDB database support via Drogon's built-in async ORM. " +
                        "Requires libmariadb-dev or libmysqlclient-dev on the build system.")
                .category(DependencyCategory.DATABASE)
                .builtIn(true)
                .configJsonKey("db_clients")
                .configJsonSnippet("""
                        {
                            "name": "default",
                            "rdbms": "mysql",
                            "host": "127.0.0.1",
                            "port": 3306,
                            "dbname": "your_database",
                            "user": "root",
                            "passwd": "",
                            "is_fast": false,
                            "number_of_connections": 4,
                            "timeout": 10.0
                        }""")
                .requiredSystemPackages(List.of("default-libmysqlclient-dev"))
                .exampleFileName("ExampleDbController.cc")
                .exampleCode("""
                        #include <drogon/HttpController.h>
                        #include <drogon/orm/DbClient.h>

                        using namespace drogon;
                        using namespace drogon::orm;

                        /**
                         * Example controller demonstrating MySQL/MariaDB database access.
                         *
                         * Prerequisites:
                         *   1. Install client lib: sudo apt install default-libmysqlclient-dev
                         *   2. Rebuild Drogon with MySQL support
                         *   3. Configure db_clients in config.json
                         *   4. Create your database and tables
                         */
                        class ExampleDbController : public HttpController<ExampleDbController>
                        {
                        public:
                            METHOD_LIST_BEGIN
                            ADD_METHOD_TO(ExampleDbController::getUsers, "/api/users", Get);
                            METHOD_LIST_END

                            void getUsers(const HttpRequestPtr &req,
                                          std::function<void(const HttpResponsePtr &)> &&callback)
                            {
                                auto dbClient = app().getDbClient("default");

                                dbClient->execSqlAsync(
                                    "SELECT id, name, email FROM users LIMIT 100",
                                    [callback](const Result &result) {
                                        Json::Value ret(Json::arrayValue);
                                        for (const auto &row : result) {
                                            Json::Value user;
                                            user["id"] = row["id"].as<int>();
                                            user["name"] = row["name"].as<std::string>();
                                            user["email"] = row["email"].as<std::string>();
                                            ret.append(user);
                                        }
                                        auto resp = HttpResponse::newHttpJsonResponse(ret);
                                        callback(resp);
                                    },
                                    [callback](const DrogonDbException &e) {
                                        Json::Value err;
                                        err["error"] = e.base().what();
                                        auto resp = HttpResponse::newHttpJsonResponse(err);
                                        resp->setStatusCode(k500InternalServerError);
                                        callback(resp);
                                    });
                            }
                        };
                        """)
                .build());

        // --- SQLite3 ---
        register(DependencyDefinition.builder()
                .id("sqlite3")
                .name("SQLite3 Driver")
                .description("SQLite3 embedded database support via Drogon's built-in ORM. " +
                        "No external server needed — data stored in a local file.")
                .category(DependencyCategory.DATABASE)
                .builtIn(true)
                .configJsonKey("db_clients")
                .configJsonSnippet("""
                        {
                            "name": "default",
                            "rdbms": "sqlite3",
                            "filename": "./app_data.db",
                            "number_of_connections": 1,
                            "timeout": 5.0
                        }""")
                .requiredSystemPackages(List.of("libsqlite3-dev"))
                .exampleFileName("ExampleDbController.cc")
                .exampleCode("""
                        #include <drogon/HttpController.h>
                        #include <drogon/orm/DbClient.h>

                        using namespace drogon;
                        using namespace drogon::orm;

                        /**
                         * Example controller demonstrating SQLite3 database access.
                         *
                         * Prerequisites:
                         *   1. Install SQLite3: sudo apt install libsqlite3-dev
                         *   2. Rebuild Drogon with SQLite3 support
                         *   3. Configure db_clients in config.json (filename path)
                         */
                        class ExampleDbController : public HttpController<ExampleDbController>
                        {
                        public:
                            METHOD_LIST_BEGIN
                            ADD_METHOD_TO(ExampleDbController::getItems, "/api/items", Get);
                            METHOD_LIST_END

                            void getItems(const HttpRequestPtr &req,
                                          std::function<void(const HttpResponsePtr &)> &&callback)
                            {
                                auto dbClient = app().getDbClient("default");

                                dbClient->execSqlAsync(
                                    "SELECT id, name FROM items LIMIT 100",
                                    [callback](const Result &result) {
                                        Json::Value ret(Json::arrayValue);
                                        for (const auto &row : result) {
                                            Json::Value item;
                                            item["id"] = row["id"].as<int>();
                                            item["name"] = row["name"].as<std::string>();
                                            ret.append(item);
                                        }
                                        auto resp = HttpResponse::newHttpJsonResponse(ret);
                                        callback(resp);
                                    },
                                    [callback](const DrogonDbException &e) {
                                        Json::Value err;
                                        err["error"] = e.base().what();
                                        auto resp = HttpResponse::newHttpJsonResponse(err);
                                        resp->setStatusCode(k500InternalServerError);
                                        callback(resp);
                                    });
                            }
                        };
                        """)
                .build());

        // --- Redis ---
        register(DependencyDefinition.builder()
                .id("redis")
                .name("Redis Client")
                .description("Redis in-memory data store support via Drogon's built-in async client. " +
                        "Requires libhiredis-dev on the build system.")
                .category(DependencyCategory.CACHING)
                .builtIn(true)
                .configJsonKey("redis_clients")
                .configJsonSnippet("""
                        {
                            "name": "default",
                            "host": "127.0.0.1",
                            "port": 6379,
                            "passwd": "",
                            "db": 0,
                            "is_fast": false,
                            "number_of_connections": 4,
                            "timeout": 3.0
                        }""")
                .requiredSystemPackages(List.of("libhiredis-dev"))
                .exampleFileName("ExampleRedisController.cc")
                .exampleCode("""
                        #include <drogon/HttpController.h>

                        using namespace drogon;

                        /**
                         * Example controller demonstrating Redis cache operations.
                         *
                         * Prerequisites:
                         *   1. Install hiredis: sudo apt install libhiredis-dev
                         *   2. Rebuild Drogon with Redis support
                         *   3. Configure redis_clients in config.json
                         *   4. Run a Redis server (e.g., docker run -p 6379:6379 redis)
                         */
                        class ExampleRedisController : public HttpController<ExampleRedisController>
                        {
                        public:
                            METHOD_LIST_BEGIN
                            ADD_METHOD_TO(ExampleRedisController::setCache, "/api/cache/{key}", Post);
                            ADD_METHOD_TO(ExampleRedisController::getCache, "/api/cache/{key}", Get);
                            METHOD_LIST_END

                            void setCache(const HttpRequestPtr &req,
                                          std::function<void(const HttpResponsePtr &)> &&callback,
                                          const std::string &key)
                            {
                                auto redisClient = app().getRedisClient("default");
                                auto body = req->getBody();

                                redisClient->execCommandAsync(
                                    [callback, key](const nosql::RedisResult &result) {
                                        Json::Value ret;
                                        ret["status"] = "ok";
                                        ret["key"] = key;
                                        auto resp = HttpResponse::newHttpJsonResponse(ret);
                                        callback(resp);
                                    },
                                    [callback](const std::exception &err) {
                                        Json::Value ret;
                                        ret["error"] = err.what();
                                        auto resp = HttpResponse::newHttpJsonResponse(ret);
                                        resp->setStatusCode(k500InternalServerError);
                                        callback(resp);
                                    },
                                    "SETEX %s %d %s", key.c_str(), 3600, std::string(body).c_str());
                            }

                            void getCache(const HttpRequestPtr &req,
                                          std::function<void(const HttpResponsePtr &)> &&callback,
                                          const std::string &key)
                            {
                                auto redisClient = app().getRedisClient("default");

                                redisClient->execCommandAsync(
                                    [callback, key](const nosql::RedisResult &result) {
                                        Json::Value ret;
                                        ret["key"] = key;
                                        if (result.type() == nosql::RedisResultType::kNil) {
                                            ret["value"] = Json::nullValue;
                                        } else {
                                            ret["value"] = result.asString();
                                        }
                                        auto resp = HttpResponse::newHttpJsonResponse(ret);
                                        callback(resp);
                                    },
                                    [callback](const std::exception &err) {
                                        Json::Value ret;
                                        ret["error"] = err.what();
                                        auto resp = HttpResponse::newHttpJsonResponse(ret);
                                        resp->setStatusCode(k500InternalServerError);
                                        callback(resp);
                                    },
                                    "GET %s", key.c_str());
                            }
                        };
                        """)
                .build());
    }

    // ==================== EXTERNAL DEPENDENCIES ====================

    private void registerExternalDependencies() {

        // --- nlohmann-json ---
        register(DependencyDefinition.builder()
                .id("nlohmann-json")
                .name("JSON for Modern C++")
                .description("Type-safe, intuitive JSON library by nlohmann. " +
                        "Supports automatic struct serialization with NLOHMANN_DEFINE_TYPE macros.")
                .category(DependencyCategory.SERIALIZATION)
                .builtIn(false)
                .cmakeFetchContent("""
                        FetchContent_Declare(
                            nlohmann_json
                            GIT_REPOSITORY https://github.com/nlohmann/json.git
                            GIT_TAG        v3.11.3
                        )
                        FetchContent_MakeAvailable(nlohmann_json)""")
                .cmakeLinkTarget("nlohmann_json::nlohmann_json")
                .requiredSystemPackages(List.of())
                .exampleFileName("ExampleJsonController.cc")
                .exampleCode("""
                        #include <drogon/HttpController.h>
                        #include <nlohmann/json.hpp>

                        using namespace drogon;

                        /**
                         * Example controller demonstrating nlohmann::json usage.
                         *
                         * nlohmann::json provides a more modern C++ JSON interface compared
                         * to Drogon's built-in Json::Value (jsoncpp). It supports automatic
                         * struct serialization via NLOHMANN_DEFINE_TYPE_NON_INTRUSIVE.
                         */

                        struct UserDto {
                            std::string username;
                            std::string email;
                            int age;
                        };

                        // Auto-generate to_json / from_json for UserDto
                        NLOHMANN_DEFINE_TYPE_NON_INTRUSIVE(UserDto, username, email, age)

                        class ExampleJsonController : public HttpController<ExampleJsonController>
                        {
                        public:
                            METHOD_LIST_BEGIN
                            ADD_METHOD_TO(ExampleJsonController::parseUser, "/api/json-example", Post);
                            METHOD_LIST_END

                            void parseUser(const HttpRequestPtr &req,
                                           std::function<void(const HttpResponsePtr &)> &&callback)
                            {
                                try {
                                    auto parsed = nlohmann::json::parse(req->getBody());
                                    UserDto user = parsed.get<UserDto>();

                                    nlohmann::json response = {
                                        {"status", "success"},
                                        {"message", "Parsed user: " + user.username},
                                        {"user", parsed}
                                    };

                                    auto resp = HttpResponse::newHttpResponse();
                                    resp->setContentTypeCode(CT_APPLICATION_JSON);
                                    resp->setBody(response.dump(2));
                                    callback(resp);
                                } catch (const std::exception &e) {
                                    auto resp = HttpResponse::newHttpResponse();
                                    resp->setStatusCode(k400BadRequest);
                                    resp->setBody(std::string("Parse error: ") + e.what());
                                    callback(resp);
                                }
                            }
                        };
                        """)
                .build());

        // --- spdlog ---
        register(DependencyDefinition.builder()
                .id("spdlog")
                .name("spdlog — Fast C++ Logging")
                .description("Very fast, header-only/compiled C++ logging library. " +
                        "Supports file rotation, async logging, custom sinks, and structured output.")
                .category(DependencyCategory.LOGGING)
                .builtIn(false)
                .cmakeFetchContent("""
                        FetchContent_Declare(
                            spdlog
                            GIT_REPOSITORY https://github.com/gabime/spdlog.git
                            GIT_TAG        v1.15.3
                        )
                        FetchContent_MakeAvailable(spdlog)""")
                .cmakeLinkTarget("spdlog::spdlog")
                .requiredSystemPackages(List.of())
                .exampleFileName("ExampleLoggingController.cc")
                .exampleCode("""
                        #include <drogon/HttpController.h>
                        #include <spdlog/spdlog.h>
                        #include <spdlog/sinks/rotating_file_sink.h>

                        using namespace drogon;

                        /**
                         * Example controller demonstrating spdlog integration.
                         *
                         * spdlog offers advanced features beyond Drogon's built-in logging:
                         * - Rotating file sinks
                         * - Async logging
                         * - Structured/JSON output
                         * - Custom log levels per logger
                         */
                        class ExampleLoggingController : public HttpController<ExampleLoggingController>
                        {
                        public:
                            ExampleLoggingController()
                            {
                                // Create a rotating file logger (5MB max, 3 rotated files)
                                auto fileSink = std::make_shared<spdlog::sinks::rotating_file_sink_mt>(
                                    "logs/app.log", 1024 * 1024 * 5, 3);
                                logger_ = std::make_shared<spdlog::logger>("app", fileSink);
                                logger_->set_level(spdlog::level::debug);
                                logger_->set_pattern("[%Y-%m-%d %H:%M:%S.%e] [%l] [%t] %v");
                            }

                            METHOD_LIST_BEGIN
                            ADD_METHOD_TO(ExampleLoggingController::testLogging, "/api/log-test", Get);
                            METHOD_LIST_END

                            void testLogging(const HttpRequestPtr &req,
                                             std::function<void(const HttpResponsePtr &)> &&callback)
                            {
                                logger_->info("Request received from {}", req->getPeerAddr().toIp());
                                logger_->debug("Query string: {}", req->getQuery());

                                Json::Value ret;
                                ret["message"] = "Check logs/app.log for output";
                                auto resp = HttpResponse::newHttpJsonResponse(ret);
                                callback(resp);
                            }

                        private:
                            std::shared_ptr<spdlog::logger> logger_;
                        };
                        """)
                .build());

        // --- fmt ---
        register(DependencyDefinition.builder()
                .id("fmt")
                .name("{fmt} — Modern Formatting")
                .description("Fast, safe, and portable formatting library. " +
                        "Provides fmt::format() as a modern alternative to printf/sprintf.")
                .category(DependencyCategory.FORMATTING)
                .builtIn(false)
                .cmakeFetchContent("""
                        FetchContent_Declare(
                            fmt
                            GIT_REPOSITORY https://github.com/fmtlib/fmt.git
                            GIT_TAG        11.1.4
                        )
                        FetchContent_MakeAvailable(fmt)""")
                .cmakeLinkTarget("fmt::fmt")
                .requiredSystemPackages(List.of())
                .exampleFileName("ExampleFmtController.cc")
                .exampleCode("""
                        #include <drogon/HttpController.h>
                        #include <fmt/format.h>
                        #include <fmt/chrono.h>

                        using namespace drogon;

                        /**
                         * Example controller demonstrating fmt library usage.
                         *
                         * fmt::format provides type-safe, fast string formatting with a
                         * Python-like syntax. Much safer than sprintf and faster than
                         * std::stringstream.
                         */
                        class ExampleFmtController : public HttpController<ExampleFmtController>
                        {
                        public:
                            METHOD_LIST_BEGIN
                            ADD_METHOD_TO(ExampleFmtController::greet, "/api/greet/{name}", Get);
                            METHOD_LIST_END

                            void greet(const HttpRequestPtr &req,
                                       std::function<void(const HttpResponsePtr &)> &&callback,
                                       const std::string &name)
                            {
                                auto now = std::chrono::system_clock::now();
                                std::string message = fmt::format(
                                    "Hello, {}! The current time is {:%Y-%m-%d %H:%M:%S}.",
                                    name, now);

                                Json::Value ret;
                                ret["message"] = message;
                                ret["formatted_number"] = fmt::format("{:>10.2f}", 3.14159);
                                auto resp = HttpResponse::newHttpJsonResponse(ret);
                                callback(resp);
                            }
                        };
                        """)
                .build());

        // --- jwt-cpp ---
        register(DependencyDefinition.builder()
                .id("jwt-cpp")
                .name("jwt-cpp — JWT Authentication")
                .description("Header-only library for creating and verifying JSON Web Tokens. " +
                        "Supports HS256, RS256, ES256 and more. Requires OpenSSL.")
                .category(DependencyCategory.AUTHENTICATION)
                .builtIn(false)
                .cmakeFetchContent("""
                        FetchContent_Declare(
                            jwt-cpp
                            GIT_REPOSITORY https://github.com/Thalhammer/jwt-cpp.git
                            GIT_TAG        v0.7.0
                        )
                        set(JWT_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
                        FetchContent_MakeAvailable(jwt-cpp)""")
                .cmakeLinkTarget("jwt-cpp::jwt-cpp")
                .requiredSystemPackages(List.of("libssl-dev"))
                .exampleFileName("ExampleAuthController.cc")
                .exampleCode("""
                        #include <drogon/HttpController.h>
                        #include <jwt-cpp/jwt.h>

                        using namespace drogon;

                        /**
                         * Example controller demonstrating JWT token generation and verification.
                         *
                         * Prerequisites:
                         *   - OpenSSL must be installed (usually already available with Drogon)
                         *
                         * In production, store the secret securely (env var or config) and
                         * implement this as a Drogon Filter for route protection.
                         */
                        class ExampleAuthController : public HttpController<ExampleAuthController>
                        {
                        public:
                            METHOD_LIST_BEGIN
                            ADD_METHOD_TO(ExampleAuthController::login, "/api/auth/login", Post);
                            ADD_METHOD_TO(ExampleAuthController::verify, "/api/auth/verify", Post);
                            METHOD_LIST_END

                            void login(const HttpRequestPtr &req,
                                       std::function<void(const HttpResponsePtr &)> &&callback)
                            {
                                auto json = req->getJsonObject();
                                if (!json || !json->isMember("username")) {
                                    auto resp = HttpResponse::newHttpResponse();
                                    resp->setStatusCode(k400BadRequest);
                                    resp->setBody("Missing username");
                                    callback(resp);
                                    return;
                                }

                                std::string username = (*json)["username"].asString();

                                // Create JWT token
                                auto token = jwt::create()
                                    .set_issuer("drogon-app")
                                    .set_subject(username)
                                    .set_issued_at(std::chrono::system_clock::now())
                                    .set_expires_at(std::chrono::system_clock::now() + std::chrono::hours{24})
                                    .sign(jwt::algorithm::hs256{"your-secret-key"});

                                Json::Value ret;
                                ret["token"] = token;
                                ret["expires_in"] = 86400;
                                auto resp = HttpResponse::newHttpJsonResponse(ret);
                                callback(resp);
                            }

                            void verify(const HttpRequestPtr &req,
                                        std::function<void(const HttpResponsePtr &)> &&callback)
                            {
                                auto json = req->getJsonObject();
                                if (!json || !json->isMember("token")) {
                                    auto resp = HttpResponse::newHttpResponse();
                                    resp->setStatusCode(k400BadRequest);
                                    resp->setBody("Missing token");
                                    callback(resp);
                                    return;
                                }

                                try {
                                    auto decoded = jwt::decode((*json)["token"].asString());
                                    auto verifier = jwt::verify()
                                        .allow_algorithm(jwt::algorithm::hs256{"your-secret-key"})
                                        .with_issuer("drogon-app");
                                    verifier.verify(decoded);

                                    Json::Value ret;
                                    ret["valid"] = true;
                                    ret["subject"] = decoded.get_subject();
                                    auto resp = HttpResponse::newHttpJsonResponse(ret);
                                    callback(resp);
                                } catch (const std::exception &e) {
                                    Json::Value ret;
                                    ret["valid"] = false;
                                    ret["error"] = e.what();
                                    auto resp = HttpResponse::newHttpJsonResponse(ret);
                                    resp->setStatusCode(k401Unauthorized);
                                    callback(resp);
                                }
                            }
                        };
                        """)
                .build());
    }

    // ==================== HELPERS ====================

    private void register(DependencyDefinition def) {
        dependencies.put(def.getId(), def);
    }
}
