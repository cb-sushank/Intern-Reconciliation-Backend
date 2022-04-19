package com.cb.reconciliation.controller;

import com.cb.reconciliation.model.*;
import com.cb.reconciliation.model.credentials.*;
import com.cb.reconciliation.persistence.Job;
import com.cb.reconciliation.persistence.JobRepository;
import com.cb.reconciliation.service.ChargebeeConnect;
import com.cb.reconciliation.service.ConvertToJSONSimple;
import com.cb.reconciliation.service.JobService;
import com.cb.reconciliation.service.MismatchedTransactions;
import org.json.JSONException;
import org.json.JSONString;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("api/v1/")
public class MatchedTransactionController {

    String xeroTenantId = "";
    String refreshToken = "";
    String clientId = "";
    String clientSecret = "";
    // XeroCredentials cred = new XeroCredentials(clientId, clientSecret, refreshToken, xeroTenantId);
    String accessToken = "";
    XeroCredentials xeroCredentials = new XeroCredentials(xeroTenantId, accessToken);

    String chargebeeSiteUrl = "";
    String chargebeeApiKey = "";
    ChargebeeCredentials chargebeeCredentials = new ChargebeeCredentials(chargebeeSiteUrl, chargebeeApiKey);

    String stripeApiKey = "";
    StripeCredentials stripeCredentials = new StripeCredentials(stripeApiKey);

    Map<GatewayEnum, GatewayCredentials> gatewayCredentialsMap = new HashMap<>();
    Map<AccSoftEnum, AccSoftCredentials> accSoftCredentialsMap = new HashMap<>();

    @GetMapping("reconciliation/mismatched")
    public JSONObject getMismatched(@RequestParam("start") String start, @RequestParam("end") String end) throws Exception {
        Timestamp startTime = new Timestamp(Long.parseLong(start) *1000);
        Timestamp endTime = new Timestamp(Long.parseLong(end) * 1000);

        MismatchedTransactions computer = new MismatchedTransactions();

        gatewayCredentialsMap.put(GatewayEnum.STRIPE, stripeCredentials);
        accSoftCredentialsMap.put(AccSoftEnum.XERO, xeroCredentials);

        List<Transaction> finalList = computer.mismatched(chargebeeCredentials, gatewayCredentialsMap, accSoftCredentialsMap, startTime, endTime);
        return ConvertToJSONSimple.transactions(finalList);
    }

    @Autowired
    JobService jobService;

    @Autowired
    JobRepository repository;

    @GetMapping("/job/status/{id}")
    public JSONObject getJobStatus(@PathVariable("id") String jobId) throws JSONException {
        System.out.println("GET /job/" + jobId + "/status");
//        System.out.println(jobService.getJobById(jobId));
        return jobService.getJobStatus(jobId);
    }

    @GetMapping("/job/{id}")
    public JSONObject getJob(@PathVariable("id") String jobId) throws JSONException, ParseException {
        System.out.println("GET /job/" + jobId);
//        System.out.println(jobService.getJob(jobId));
        return jobService.getJob(jobId);
    }

    @PostMapping("/job/")
    public JSONObject getAllJob(@RequestBody JobFilter jobFilter) throws JSONException, ParseException {
        System.out.println("GET /job/");
        return jobService.getAllJob(jobFilter);
    }

    @PostMapping("/reconcile")
    public JSONObject createJob(@RequestBody JobArguments arguments) throws Exception {
        System.out.println("POST /reconcile");

        String jobId = UUID.randomUUID().toString();
        Timestamp startTime = new Timestamp(Long.parseLong(arguments.getStart()) *1000);
        Timestamp endTime = new Timestamp(Long.parseLong(arguments.getEnd()) *1000);
        String gateway = arguments.getGateway();
        String siteUrl = arguments.getSiteUrl();

        Timestamp createdAt = Timestamp.valueOf(LocalDateTime.now());
        String status = "PROCESSING";

        Job newJob = Job.builder()
                .jobId(jobId)
                .chargebeeSiteUrl(siteUrl)
                .gateway(gateway)
                .startTime(startTime)
                .endTime(endTime)
                .status(status)
                .createdAt(createdAt)
                .build();
//        System.out.println(newJob);
        repository.save(newJob);

        // credentials
        gatewayCredentialsMap.put(GatewayEnum.STRIPE, stripeCredentials);
        accSoftCredentialsMap.put(AccSoftEnum.XERO, xeroCredentials);

        jobService.executeJob(jobId, chargebeeCredentials, gatewayCredentialsMap, accSoftCredentialsMap);

        JSONObject response = new JSONObject();
        response.put("jobId", jobId);
        return response;
    }

    @GetMapping("/chb_txns")
    public List<Transaction> getCbhTxns(@RequestBody JobArguments arguments) throws Exception {
        System.out.println("POST /chb_txns");

        String jobId = UUID.randomUUID().toString();
        Timestamp startTime = new Timestamp(Long.parseLong(arguments.getStart()) *1000);
        Timestamp endTime = new Timestamp(Long.parseLong(arguments.getEnd()) *1000);
        String gateway = arguments.getGateway();
        String siteUrl = arguments.getSiteUrl();

        ChargebeeConnect chargebeeConnect = new ChargebeeConnect();
        List<Transaction> chargebeeTransactions = chargebeeConnect.getTransactionsByGateway(
                chargebeeCredentials,
                GatewayEnum.STRIPE,
                startTime,
                endTime);

        return chargebeeTransactions;
    }

    @GetMapping("/site_url")
    public String getsiteUrl() throws Exception {
        System.out.println("Get /getsiteUrl");
        int pos=chargebeeSiteUrl.lastIndexOf("-");
        if(pos==-1){
            return "Error";
        }
        return chargebeeSiteUrl.substring(0,pos);
    }
}
