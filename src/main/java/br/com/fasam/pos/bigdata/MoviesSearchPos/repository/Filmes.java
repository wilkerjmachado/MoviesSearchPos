package br.com.fasam.pos.bigdata.MoviesSearchPos.repository;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsRequest;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsResponse;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.IndicesAdminClient;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.TransportAddress;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.elasticsearch.transport.client.PreBuiltTransportClient;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
public class Filmes {

	private static final String TYPE = "movies";
	private static final String INDEX = "site";
	private TransportClient client;
	private IndicesAdminClient adminClient;

	@SuppressWarnings("resource")
	public Filmes() {
		
		Settings settings = Settings.builder().put("cluster.name", "docker-cluster").build();
		
		InetSocketAddress transportAddress;
		
		try {
			transportAddress = new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 9300);
			
			this.client = new PreBuiltTransportClient(settings).addTransportAddress(new TransportAddress(transportAddress));
			
			this.adminClient = client.admin().indices();
			
		} catch (UnknownHostException e) {
			
			e.printStackTrace();
		}

		if (!indexExists()) {

			try {

				this.criarIndiceMapeado();

				Reader in = new FileReader("movies_metadata.csv");

				Scanner scan = new Scanner(new File("movies_metadata.csv"));

				String header = scan.nextLine();
				String[] headerVals = header.split(",");

				Iterable<CSVRecord> records = CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(in);

				BulkRequestBuilder prepareBulk = client.prepareBulk();
				int bulkCount = 0;
				for (CSVRecord record : records) {
					Map<String, Object> movie = new HashMap<>();

					int count = 0;
					for (String s : headerVals) {
						try {
							movie.put(s, record.get(count++));
						} catch (Exception e2) {
							break;
						}
					}
					IndexRequestBuilder source = client.prepareIndex(INDEX, TYPE).setSource(movie);
					prepareBulk.add(source);
					bulkCount++;
					// source.get();
					if (bulkCount > 500) {
						prepareBulk.get();
						bulkCount = 0;
						prepareBulk = client.prepareBulk();
					}

				}
				
			} catch (IOException e1) {
				
				e1.printStackTrace();
			}

		}

	}

	private boolean indexExists() {
		IndicesExistsRequest request = new IndicesExistsRequest(INDEX);
		IndicesExistsResponse response = adminClient.exists(request).actionGet();
		return response.isExists();
	}

	private void criarIndiceMapeado() {

		try {

			XContentBuilder mapping = jsonBuilder().startObject()
					.startObject("properties")
						.startObject("vote_average").field("type", "double").endObject()
						.startObject("title").field("type", "keyword").endObject()
						.startObject("release_date").field("type", "date").endObject()
					.endObject()
			.endObject();

			client.admin().indices().prepareCreate(INDEX).addMapping(TYPE, mapping).execute().actionGet();

		} catch (IOException e) {

			e.printStackTrace();
		}

	}

	public List<Map<String, Object>> getTopFilmes() {

		List<Map<String, Object>> filmes = new ArrayList<Map<String, Object>>();
		// Seu código deve vir daqui para baixo

		FieldSortBuilder sort = new FieldSortBuilder("vote_average").order(SortOrder.DESC);

		SearchResponse response = client.prepareSearch(INDEX).setTypes(TYPE).addSort(sort).execute().actionGet();

		SearchHit[] results = response.getHits().getHits();
		
		for (SearchHit hit : results) {
			System.out.println("------------------------------");
			Map<String, Object> result = hit.getSourceAsMap();
			filmes.add(result);
			System.out.println(result);
		}
		
		return filmes;
	}

	public List<Map<String, Object>> getSearchFilmes(String titulo, String desc, Integer ano) {
		
		List<Map<String, Object>> filmes = new ArrayList<Map<String, Object>>();
		// Seu código deve vir daqui para baixo
			
		BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
		
		if (!StringUtils.isEmpty(titulo)) boolQueryBuilder.must().add(QueryBuilders.matchQuery("title", titulo));
		
		if (!StringUtils.isEmpty(desc)) boolQueryBuilder.must().add(QueryBuilders.matchQuery("overview", desc));
		
		if (ano != null && ano <= Calendar.getInstance().get(Calendar.YEAR)) {
			
			 boolQueryBuilder.must().add(QueryBuilders.rangeQuery("release_date").gte(ano + "||/y").lte("now/y").format("yyyy"));
			 
		}

		SearchResponse response = client.prepareSearch(INDEX).setTypes(TYPE)
				  .setSearchType(SearchType.DFS_QUERY_THEN_FETCH)
				  .setQuery(boolQueryBuilder)
				  .execute().actionGet();

		SearchHit[] results = response.getHits().getHits();
		
		for (SearchHit hit : results) {
			System.out.println("------------------------------");
			Map<String, Object> result = hit.getSourceAsMap();
			filmes.add(result);
			System.out.println(result);
		}
		
		return filmes;
	}

	@Override
	protected void finalize() throws Throwable {
		super.finalize();
		this.client.close();
	}
}
