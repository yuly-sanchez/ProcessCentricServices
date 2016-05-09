package introsde.finalproject.rest.processcentricservices.resources;

import introsde.finalproject.rest.processcentricservices.util.UrlInfo;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import javax.ejb.LocalBean;
import javax.ejb.Stateless;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.glassfish.jersey.client.ClientConfig;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.XML;

@Stateless
@LocalBean
public class PersonResource {

	@Context
	UriInfo uriInfo;
	@Context
	Request request;

	private UrlInfo urlInfo;
	private String businessLogicServiceURL;
	private String storageServiceURL;

	private static String mediaType = MediaType.APPLICATION_JSON;

	/**
	 * initialize the connection with the Business Logic Service (SS)
	 */
	public PersonResource(UriInfo uriInfo, Request request) {
		this.uriInfo = uriInfo;
		this.request = request;

		this.urlInfo = new UrlInfo();
		this.businessLogicServiceURL = urlInfo.getBusinesslogicURL();
		this.storageServiceURL = urlInfo.getStorageURL();
	}

	private String errorMessage(Exception e) {
		return "{ \n \"error\" : \"Error in Process Centric Services, due to the exception: "
				+ e + "\"}";
	}

	private String externalErrorMessageSS(String e) {
		return "{ \n \"error\" : \"Error in External Storage Services, due to the exception: "
				+ e + "\"}";
	}

	private String externalErrorMessageBLS(String e) {
		return "{ \n \"error\" : \"Error in External BUsiness Logic Services, due to the exception: "
				+ e + "\"}";
	}

	// ******************* PERSON ***********************

	/**
	 * PUT /person/{idPerson}/checkCurrentHealth/{measureName} 
	 * I Integration Logic:
	 * 
	 * checkCurrentHealth(idPerson, inputMeasureJSON, measureName) calls
	 * <ul>readPersonDetails() method in Business Logic Services</ul>
	 * <ul>updateMeasure(Measure m) method in Storage Services</ul>
	 * <ul>comparisonValueOfMeasure(idPerson, inputMeasureJSON, measureName) method in Business Logic Services</ul>
	 * <ul>readMotivationHealth(idPerson, measureName) method in Business Logic Services</ul>
	 * <ul>readMotivationGoal(idPerson, measureName) method in Business Logic Services</ul>
	 * <ul>getPicture() method in Storage Services</ul>
	 * 
	 * @return
	 */
	@PUT
	@Path("{pid}/checkCurrentHealth/{measureName}")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Response checkCurrentHealth(@PathParam("pid") int idPerson,
			String inputMeasureJSON,
			@PathParam("measureName") String measureName) throws Exception {

		System.out
				.println("checkCurrentHealth: First integration logic which calls 5 services sequentially "
						+ "from Storage and Business Logic Services in Process Centric Services...");

		// GET PERSON/{IDPERSON} --> BLS
		String path = "/person/" + idPerson;

		String xmlBuild = "";

		ClientConfig clientConfig = new ClientConfig();
		Client client = ClientBuilder.newClient(clientConfig);
		WebTarget service = client.target(businessLogicServiceURL);

		Response response = service.path(path).request().accept(mediaType)
				.get(Response.class);
		if (response.getStatus() != 200) {
			System.out
					.println("Business Logic Service Error catch response.getStatus() != 200");
			return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
					.entity(externalErrorMessageBLS(response.toString()))
					.build();
		}

		String result = response.readEntity(String.class);
		JSONObject measureTarget = null;

		JSONObject obj1 = new JSONObject(result);
		JSONObject currentObj = (JSONObject) obj1.get("currentHealth");
		JSONArray measureArr = currentObj.getJSONArray("measure");
		for (int i = 0; i < measureArr.length(); i++) {
			if (measureArr.getJSONObject(i).getString("name")
					.equals(measureName)) {
				measureTarget = measureArr.getJSONObject(i);
			}
		}

		if (measureTarget == null) {
			xmlBuild = "<measure>" + measureName + " don't exist "
					+ "</measure>";

		} else {
			System.out.println("measureID: " + measureTarget.get("mid"));
			System.out.println("measureType: " + measureTarget.get("name"));

			// PUT PERSON/{IDPERSON}/MEASURE/{IDMEASURE} --> SS
			path = "/person/" + idPerson + "/measure/"
					+ measureTarget.get("mid");

			service = client.target(storageServiceURL);
			response = service.path(path).request(mediaType)
					.put(Entity.json(inputMeasureJSON));

			if (response.getStatus() != 200) {
				System.out
						.println("Storage Service Error catch response.getStatus() != 200");
				return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
						.entity(externalErrorMessageSS(response.toString()))
						.build();
			}

			// GET PERSON/{IDPERSON}/COMPARISON-VALUE/{MEASURENAME} --> BLS
			path = "/person/" + idPerson + "/comparison-value/"
					+ measureTarget.get("name");

			DefaultHttpClient httpClient = new DefaultHttpClient();
			HttpGet request = new HttpGet(businessLogicServiceURL + path);
			HttpResponse resp = httpClient.execute(request);

			BufferedReader rd = new BufferedReader(new InputStreamReader(resp
					.getEntity().getContent()));

			StringBuffer rs = new StringBuffer();
			String line = "";
			while ((line = rd.readLine()) != null) {
				rs.append(line);
			}

			JSONObject obj2 = new JSONObject(rs.toString());

			if (resp.getStatusLine().getStatusCode() != 200) {
				System.out
						.println("Business Logic Service Error catch response.getStatus() != 200");
				return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
						.entity(externalErrorMessageBLS(response.toString()))
						.build();
			}

			JSONObject comparisonInfo = (JSONObject) obj2
					.getJSONObject("comparison-information");
			JSONObject obj3 = null;
			JSONObject res = null;

			System.out.println("measureValueUpdated: "
					+ comparisonInfo.get("measureValue"));
			System.out.println("result: " + comparisonInfo.get("result"));
			System.out.println("measure: " + comparisonInfo.get("measure"));
			System.out.println("goalValue: " + comparisonInfo.get("goalValue"));
			System.out.println("measureValue: "
					+ comparisonInfo.get("measureValue"));

			if (comparisonInfo.get("result").equals("ok")) {
				// GET PERSON/{IDPERSON}/MOTIVATION-GOAL/{MEASURENAME} --> BLS
				path = "/person/" + idPerson + "/motivation-goal/"
						+ measureTarget.get("name");
				System.out.println("path-4: " + path);

				httpClient = new DefaultHttpClient();
				request = new HttpGet(businessLogicServiceURL + path);
				resp = httpClient.execute(request);

				rd = new BufferedReader(new InputStreamReader(resp.getEntity()
						.getContent()));
				rs = new StringBuffer();
				line = "";
				while ((line = rd.readLine()) != null) {
					rs.append(line);
				}

				obj3 = new JSONObject(rs.toString());
				res = obj3.getJSONObject("motivationGoal")
						.getJSONObject("goal");
				System.out.println("quote: " + res.getString("motivation"));

				if (resp.getStatusLine().getStatusCode() != 200) {
					System.out
							.println("Business Logic Service Error catch response.getStatus() != 200");
					return Response
							.status(Response.Status.INTERNAL_SERVER_ERROR)
							.entity(externalErrorMessageBLS(response.toString()))
							.build();
				}
			} else {
				// GET PERSON/{IDPERSON}/MOTIVATION-HEALTH/{MEASURENAME} --> BLS
				path = "/person/" + idPerson + "/motivation-health/"
						+ measureTarget.get("name");

				httpClient = new DefaultHttpClient();
				request = new HttpGet(businessLogicServiceURL + path);
				resp = httpClient.execute(request);

				rd = new BufferedReader(new InputStreamReader(resp.getEntity()
						.getContent()));
				rs = new StringBuffer();
				line = "";
				while ((line = rd.readLine()) != null) {
					rs.append(line);
				}

				obj3 = new JSONObject(rs.toString());
				res = obj3.getJSONObject("motivationHealth").getJSONObject(
						"measure");
				System.out.println("quote: " + res.getString("motivation"));

				if (resp.getStatusLine().getStatusCode() != 200) {
					System.out
							.println("Business Logic Service Error catch response.getStatus() != 200");
					return Response
							.status(Response.Status.INTERNAL_SERVER_ERROR)
							.entity(externalErrorMessageBLS(response.toString()))
							.build();
				}
			}

			// GET ADAPTER/PICTURE
			path = "/adapter/picture";

			httpClient = new DefaultHttpClient();
			request = new HttpGet(storageServiceURL + path);
			resp = httpClient.execute(request);

			rd = new BufferedReader(new InputStreamReader(resp.getEntity()
					.getContent()));

			rs = new StringBuffer();
			line = "";
			while ((line = rd.readLine()) != null) {
				rs.append(line);
			}

			JSONObject obj4 = new JSONObject(rs.toString());
			System.out.println("picture_url: "
					+ obj4.getJSONObject("picture").getString("thumbUrl"));

			if (resp.getStatusLine().getStatusCode() != 200) {
				System.out
						.println("Storage Service Error catch response.getStatus() != 200");
				return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
						.entity(externalErrorMessageSS(response.toString()))
						.build();
			}

			xmlBuild = "<updatedCurrentHealth>";
			xmlBuild += "<measureID>" + measureTarget.get("mid")
					+ "</measureID>";
			xmlBuild += "<measureType>" + measureTarget.get("name")
					+ "</measureType>";
			xmlBuild += "<measureValueUpdated>"
					+ comparisonInfo.get("measureValue")
					+ "</measureValueUpdated>";
			xmlBuild += "</updatedCurrentHealth>";

			xmlBuild += "<comparisonInformation>";
			xmlBuild += "<result>" + comparisonInfo.get("result") + "</result>";
			xmlBuild += "<measure>" + comparisonInfo.get("measure")
					+ "</measure>";
			xmlBuild += "<goalValue>" + comparisonInfo.get("goalValue")
					+ "</goalValue>";
			xmlBuild += "<measureValue>"
					+ comparisonInfo.get("measureValue")
					+ "</measureValue>";
			xmlBuild += "</comparisonInformation>";

			xmlBuild += "<resultInformation>";
			xmlBuild += "<picture_url>"
					+ obj4.getJSONObject("picture").get("thumbUrl")
					+ "</picture_url>";
			xmlBuild += "<quote>" + res.get("motivation") + "</quote>";
			xmlBuild += "</resultInformation>";

		}

		JSONObject xmlJSONObj = XML.toJSONObject(xmlBuild);
		String jsonPrettyPrintString = xmlJSONObj.toString(4);

		System.out.println(jsonPrettyPrintString);

		return Response.ok(jsonPrettyPrintString).build();
	}

	
	/**
	 * POST /person/{idPerson}/checkGoal/{measureName} 
	 * II Integration Logic:
	 * 
	 * checkGoal(idPerson, inputMeasureJSON, measureName) calls
	 * <ul>readPersonDetails() method in Business Logic Services</ul>
	 * <ul>createGoal(Goal g) method in Storage Services</ul>
	 * <ul>getPerson() method in Storage Services</ul>
	 * <ul>readMotivationGoal(idPerson, measureName) method in Business Logic Services</ul>
	 * 
	 * @return
	 */
	@POST
	@Path("{pid}/checkGoal/{measureName}")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Response checkGoal(@PathParam("pid") int idPerson,
			String inputGoalJSON, @PathParam("measureName") String measureName)
			throws Exception {

		System.out
				.println("checkGoal: Second integration logic which calls 4 services sequentially "
						+ "from Storage and Business Logic Services in Process Centric Services...");

		// GET PERSON/{IDPERSON} --> BLS
		String path = "/person/" + idPerson;

		String xmlBuild = "";

		ClientConfig clientConfig = new ClientConfig();

		Client client = ClientBuilder.newClient(clientConfig);
		WebTarget service = client.target(businessLogicServiceURL);

		Response response = service.path(path).request().accept(mediaType)
				.get(Response.class);
		if (response.getStatus() != 200) {
			System.out
					.println("Business Logic Service Error catch response.getStatus() != 200");
			return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
					.entity(externalErrorMessageBLS(response.toString()))
					.build();
		}

		String result = response.readEntity(String.class);
		
		JSONObject goalTarget = null;
		JSONObject measureTarget = null;

		JSONObject obj = new JSONObject(result);
		
		JSONObject goalsObj = (JSONObject) obj.get("goals");
		JSONArray goalArr = goalsObj.getJSONArray("goal");
		for (int i = 0; i < goalArr.length(); i++) {
			if (goalArr.getJSONObject(i).getString("type").equals(measureName)) {
				goalTarget = goalArr.getJSONObject(i);
			}
		}

		if (goalTarget == null) {
			// POST PERSON/{IDPERSON}/POST --> SS
			path = "/person/" + idPerson + "/goal";
			service = client.target(storageServiceURL);

			response = service
					.path(path)
					.request()
					.accept(mediaType)
					.post(Entity.entity(inputGoalJSON, mediaType),
							Response.class);
			if (response.getStatus() != 201) {
				System.out
						.println("Storage Service Error catch response.getStatus() != 201");
				return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
						.entity(externalErrorMessageSS(response.toString()))
						.build();
			}
		}

		// GET PERSON/{IDPERSON} --> SS
		path = "/person/" + idPerson;

		client = ClientBuilder.newClient(clientConfig);
		service = client.target(storageServiceURL);

		response = service.path(path).request().accept(mediaType)
				.get(Response.class);
		if (response.getStatus() != 200) {
			System.out
					.println("Storage Service Error catch response.getStatus() != 200");
			return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
					.entity(externalErrorMessageSS(response.toString()))
					.build();
		}

		result = response.readEntity(String.class);

		obj = new JSONObject(result);
		
		JSONObject currentHealthObj = (JSONObject) obj.get("currentHealth");
		JSONArray measureArr = currentHealthObj.getJSONArray("measure");
		for (int i = 0; i < measureArr.length(); i++) {
			if (measureArr.getJSONObject(i).getString("name").equals(measureName)) {
				measureTarget = measureArr.getJSONObject(i);
			}
		}
		
		goalsObj = (JSONObject) obj.get("goals");
		goalArr = goalsObj.getJSONArray("goal");
		for (int i = 0; i < goalArr.length(); i++) {
			if (goalArr.getJSONObject(i).getString("type").equals(measureName)) {
				goalTarget = goalArr.getJSONObject(i);
			}
		}
		
		String phrase = getPhrase(goalTarget.getBoolean("achieved"), idPerson,
				measureName);

		xmlBuild = "<person>";
			xmlBuild += "<id>" + obj.get("pid") + "</id>";
			xmlBuild += "<firstname>" + obj.get("firstname") + "</firstname>";
		xmlBuild += "</person>";

		xmlBuild += "<measure>";
			xmlBuild += "<id>" + measureTarget.get("mid") + "</id>";
			xmlBuild += "<type>" + measureTarget.get("name") + "</type>";
			xmlBuild += "<value>" + measureTarget.get("value") + "</value>";
			xmlBuild += "<created>" + measureTarget.get("created") + "</created>";
		xmlBuild += "</measure>";

		xmlBuild += "<goal>";
			xmlBuild += "<id>" + goalTarget.get("gid") + "</id>";
			xmlBuild += "<type>" + goalTarget.get("type") + "</type>";
			xmlBuild += "<value>" + goalTarget.get("value") + "</value>";
			xmlBuild += "<achieved>" + goalTarget.get("achieved") + "</achieved>";
			xmlBuild += "<motivation>" + phrase + "</motivation>";
		xmlBuild += "</goal>";

		JSONObject xmlJSONObj = XML.toJSONObject(xmlBuild);
		String jsonPrettyPrintString = xmlJSONObj.toString(4);

		System.out.println(jsonPrettyPrintString);

		return Response.ok(jsonPrettyPrintString).build();

	}

	/**
	 * 
	 * @param check Boolean
	 * @return String a motivation phrase
	 */
	private String getPhrase(Boolean check, int idPerson, String measureName) {
		if (check == true) {
			return "Very good, you achieved a new goal!!! :)";
		} else {
			return getMotivationPhrase(idPerson, measureName);
		}
	}

	/**
	 * Returns a motivation phrase Calls one time the BLS
	 * 
	 * @return String
	 */
	private String getMotivationPhrase(int idPerson, String measureName) {
		String path = "/person/" + idPerson + "/motivation-goal/" + measureName;
		ClientConfig clientConfig = new ClientConfig();

		Client client = ClientBuilder.newClient(clientConfig);
		WebTarget service = client.target(businessLogicServiceURL);
		Response response = service.path(path).request().accept(mediaType)
				.get(Response.class);

		String result = response.readEntity(String.class);
		JSONObject obj = new JSONObject(result);
		return obj.getJSONObject("motivationGoal").getJSONObject("goal")
				.getString("motivation");
	}
}