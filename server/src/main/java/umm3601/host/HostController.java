package umm3601.host;

import static org.mockito.ArgumentMatchers.eq;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.swing.GroupLayout.Group;
import javax.swing.text.html.HTMLDocument.Iterator;

import org.bson.Document;
import org.bson.UuidRepresentation;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.mongojack.JacksonMongoCollection;

import com.fasterxml.jackson.databind.RuntimeJsonMappingException;
import com.mongodb.client.MongoDatabase;

import io.javalin.Javalin;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.NotFoundResponse;
import io.javalin.websocket.WsContext;
import umm3601.Controller;
import umm3601.grid.Grid;
// import umm3601.word.Search;

public class HostController implements Controller {
  // not all of these will be used
  private static final String API_HOST = "/api/hosts/{id}";
  private static final String API_HOST_GRIDS = "/api/grids/{hostId}";
  private static final String API_HOST_GRID = "/api/grid/{hostId}/{gridId}";
  private static final String WEBSOCKET_HOST = "/ws/host";

  // private static final String API_ROOM = "/api/rooms/{id}";
  // private static final String API_START_ROOM = "/api/startRoom/{id}";
  // private static final String API_STARTED_ROOM = "/api/startedRooms/{accessCode}";
  // private static final String API_END_ROOM = "/api/endRoom/{id}";
  // private static final String API_ENDED_ROOM = "/api/endedRooms/{id}";


  static final String HOST_KEY = "hostId";
  static final String ROOM_KEY = "roomId";

  private static final int ACCESS_CODE_MIN = 100000;
  private static final int ACCESS_CODE_RANGE = 900000;
  private static final int ACCESS_CODE_LENGTH = 6;

  private static final int WEB_SOCKET_PING_INTERVAL = 5;

  private final JacksonMongoCollection<Host> hostCollection;
  private final JacksonMongoCollection<Grid> gridCollection;
  // private final JacksonMongoCollection<Search> searchCollection;

  private HashSet<WsContext> connectedContexts = new HashSet<>();

  public HostController(MongoDatabase database) {
    hostCollection = JacksonMongoCollection.builder().build(
        database,
        "hosts",
        Host.class,
        UuidRepresentation.STANDARD);
    gridCollection = JacksonMongoCollection.builder().build(
        database,
        "grids",
        Grid.class,
        UuidRepresentation.STANDARD);
    // searchCollection = JacksonMongoCollection.builder().build(
    //     database,
    //     "searches",
    //     Search.class,
    //     UuidRepresentation.STANDARD);
  }

  public void getHost(Context ctx) {
    String id = ctx.pathParam("hostId");
    Host host;

    try {
      host = hostCollection.find(eq("_id", new ObjectId(id))).first();
    } catch (IllegalArgumentException e) {
      throw new BadRequestResponse("The requested host id wasn't a legal Mongo Object ID.");
    }
    if (host == null) {
      throw new NotFoundResponse("The requested host was not found");
    } else {
      ctx.json(host);
      ctx.status(HttpStatus.OK);
    }
  }

  public Grid getGrid(Context ctx) {
    String id = ctx.pathParam("gridId")
    Grid grid;

    try {
      grid = gridCollection.find(eq("_id", new ObjectId(id))).first();
    } catch (IllegalArgumentException e) {
      throw new BadRequestResponse("The requested grid id wasn't a legal Mongo Object ID.");
    }
    if (grid == null) {
      throw new NotFoundResponse("The requested grid was not found");
    } else {
      return grid;
    }
  }
  public void getGrids(Context ctx) {
    List<Bson> filters = new ArrayList<>();
    if (ctx.queryParamMap().containsKey(HOST_KEY)) {
      String targetHost = ctx.queryParamAsClass(HOST_KEY, String.class).get();
      filters.add(eq(HOST_KEY, targetHost));
    }
    Bson combinedFilter = filters.isEmpty() ? new Document() : and(filters);

    ArrayList<Grid> matchingGrids = gridCollection
        .find(combinedFilter)
        .into(new ArrayList<>());

    ctx.json(matchingGrids);
    ctx.status(HttpStatus.OK);
  }
  public void addNewGrid(Context ctx) {
    // this is the same as in grid Service (names changed)
    String body = ctx.body();
    Grid newGrid = ctx.bodyValidator(Grid.class)
        .check(td -> td.owner != null, "Owner must be non-empty")
        .check(td -> td.grid != null, "Error with grid, grid was : " + body)
        .getOrThrow(m -> new RuntimeJsonMappingException("Failed to parse body as grid: " + body));
    gridCollection.insertOne(newGrid);
    ctx.json(Map.of("gridId", newGrid._id));
    ctx.status(HttpStatus.CREATED);
  }
public void updateListeners(Map<String, String> events) {
    Iterator<WsContext> iterator = connectedContexts.iterator();
    while (iterator.hasNext()) {
      WsContext ws = iterator.next();
      if (ws.session.isOpen()) {
        ws.send(events);
      } else {
        iterator.remove();
      }
    }
  }

  public void addConnectedContext(WsContext context) {
    this.connectedContexts.add(context);
  }

  public ArrayList<WsContext> getConnectedContexts() {
    return new ArrayList<>(this.connectedContexts);
  }

  public void createAndSendEvent(String event, String data) {
    Map<String, String> events = createEvent(event, data);
    updateListeners(events);
  }

  public void handleWebSocketConnections(Javalin server) {
    server.ws(WEBSOCKET_HOST, ws -> {
      ws.onConnect(ctx -> {
        addConnectedContext(ctx);
        ctx.enableAutomaticPings(WEB_SOCKET_PING_INTERVAL, TimeUnit.SECONDS);
      });
    });
  }

  @Override
  public void addRoutes(Javalin server) {
    server.get(API_HOST_GRID, this::getGrid);
    server.get(API_HOST_GRIDS, this::getGrids);
    server.post(API_HOST_GRID, this::addNewGrid);

    handleWebSocketConnections(server);
  }
}
