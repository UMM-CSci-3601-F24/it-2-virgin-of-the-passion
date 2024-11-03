package umm3601.host;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bson.Document;
import org.bson.AbstractBsonWriter.Context;
import org.bson.types.ObjectId;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.ArgumentCaptor;

import com.mongodb.MongoClientSettings;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

import io.javalin.Javalin;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.NotFoundResponse;
import io.javalin.json.JavalinJackson;
import io.javalin.validation.Validator;
import umm3601.grid.Grid;

public class HostControllerSpec {
  private HostController hostController;
  private ObjectId kaytesId;
  private ObjectId gridId;

  private static MongoClient mongoClient;
  private static MongoDatabase db;
  private static JavalinJackson javalinJackson = new JavalinJackson();

  @Mock
  private Context ctx;

  @Captor
  private ArgumentCaptor<ArrayList<Grid>> gridArrayListCaptor;

  @Captor
  private ArgumentCaptor<Host> hostCaptor;

  @Captor
  private ArgumentCaptor<Map<String, String>> mapCaptor;

  @BeforeAll
  static void setupAll() {
    String mongoAddr = System.getenv(null).getOrDefault("MONGO_ADDR");

    mongoClient = MongoClients.create(
      MongoClientSettings.builder()
        .applyToClusterSettings(builder -> builder.hosts(Arrays.asList(new ServerAddress(mongoAddr))))
        .build());
    db = mongoClient.getDatabase("test");
  }

  @AfterAll
  static void teardown() {
    db.drop();
    mongoClient.close();
  }

  @BeforeEach
  void setupEach() throws IOException {
    MockitoAnnotations.openMocks(this);

    MongoCollection<Document> hostDoccuments = db.getCollection("hosts");
    hostDoccuments.drop();
    kaytesId = new ObjectId();
    Document Kayte = new Document().append("_id", kaytesId);

    hostDoccuments.insertOne(Kayte);

    // More db set up
    MongoCollection<Document> gridDocument = db.getCollection("grids");
    gridDocument.drop();
    List<Document> testGrids = new ArrayList<>();
      testGrids.add(
        new Document()
          .append("gridId", "gridId")
          .append("owner", testGrids)
          .append("grids", testGrids)
          .append("id", testGrids)
      );
      testGrids.add(
        new Document()
          .append("gridId", "gridId")
          .append("owner", testGrids)
          .append("grids", testGrids)
          .append("id", testGrids)
      );
    gridDocument.insertMany(testGrids);
  }

  @Test
  void addRoutes() {
    Javalin mockServer = mock(Javalin.class);
    hostController.addRoutes(mockServer);
    verify(mockServer, Mockito.atLeast(2)).get(any(), any());
    verify(mockServer, Mockito.atLeast(1).ws(any(), any()));
  }

  @Test
  void getHostByID() throws IOException {
    String id = kaytesId.toHexString();
    when(ctx.pathParam("hostId")).thenReturn(id);

    hostController.getHost(ctx);

    verify(ctx).json(hostCaptor.capture());
    verify(ctx).status(HttpStatus.OK);

    assertEquals("Kayte", hostCaptor.getValue().name);
    assertEquals(kaytesId.toHexString(), hostCaptor.getValue()._id);
  }

  @Test
  void getHostByBadId() throws IOException {
   when(ctx.pathParam("id")).thenReturn("bad");

    Throwable exception = assertThrows(BadRequestResponse.class, () -> {
      hostController.getHost(ctx);
    });

    assertEquals("The requested host id wasn't a legal Mongo Object ID.", exception.getMessage());
  }

  @Test
  void getHostByNonexistentId() throws IOException {
    String id = "588935f5c668650dc77df581";
    when(ctx.pathParam("id")).thenReturn(id);

    Throwable exception = assertThrows(NotFoundResponse.class, () -> {
      hostController.getHost(ctx);
    });

    assertEquals("The requested host was not found", exception.getMessage());
  }

  @Test
  void getGridsByHostId() throws IOException {
    Map<String, List<String>> queryParams = new HashMap<>();
    queryParams.put("hostId", Collections.singletonList("kaytesId"));
    when(ctx.queryParamMap()).thenReturn(queryParams);
    when(ctx.queryParamAsClass("hostId", String.class))
      .thenReturn(Validator.create(String.class, "kaytesId", "hostId"));
  }

  @Test
  void getGridById() throws IOException {
    Map<String, List<String>> queryParams = new HashMap<>();
    queryParams.put("hostId", Collections.singletonList("kaytesId"));
    when(ctx.queryParamMap()).thenReturn(queryParams);
    when(ctx.queryParamAsClass("hostId", String.class))
      .thenReturn(Validator.create(String.class, "kaytesId", "hostId"));

    hostController.getGrids(ctx);

    verify(ctx).json(gridArrayListCaptor.capture());
    verify(ctx).status(HttpStatus.OK);

    //check there is 2 grids in db
    assertEquals(2, gridArrayListCaptor.getValue().size());
    for (Grid grid: gridArrayListCaptor.getValue()) {
      assertEquals("kaytesId", grid.owner);
    }


  }

  @Test
  void getGridByBadID() throws IOException {

  }

  @Test
  void getGridByNonexistentID() throws IOException {

  }

  @Test
  void addNewGrid() throws IOException {
    // this is for calling save grid on a new grid

  }

  @Test
  void saveGrid() throws IOException {
    //this is for calling save grid on an old grid
  }
  @Test
  void testCreateEvent() {
    // Act
    Map<String, String> event = hostController.createEvent("testEvent", "testData");

    // Assert
    assertEquals("testData", event.get("testEvent"), "Event data should match");
    assertNotNull(event.get("timestamp"), "Timestamp should not be null");
  }

  @Test
  void testCreateAndSendEvent() {
    // Arrange
    String event = "testEvent";
    String data = "testData";
    Map<String, String> expectedEvent = Map.of(event, data, "timestamp", new Date().toString());

    // Mock the updateListeners method
    HostController spyController = Mockito.spy(hostController);
    doNothing().when(spyController).updateListeners(any());

    // Act
    spyController.createAndSendEvent(event, data);

    // Assert
    verify(spyController, times(1)).updateListeners(expectedEvent);
  }

}
