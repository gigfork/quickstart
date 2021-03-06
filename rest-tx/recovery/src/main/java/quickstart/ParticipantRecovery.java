/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011, Red Hat, Inc. and/or its affiliates,
 * and individual contributors as indicated by the @author tags.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU Lesser General Public License, v. 2.1.
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License,
 * v.2.1 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 *
 * (C) 2011,
 * @author JBoss, by Red Hat.
 */
package quickstart;

import java.net.HttpURLConnection;

import org.jboss.jbossts.star.util.TxMediaType;
import org.jboss.jbossts.star.util.TxSupport;

public class ParticipantRecovery {
    private static final int host = 0;
    private static final String[] hosts = {"localhost:8080", "184.72.71.236", "jbossapp1-mmusgrov1.dev.rhcloud.com"};
    private static final String TXN_MGR_URL = "http://" + hosts[host] + "/rest-tx/tx/transaction-manager";

    private static final int SERVICE_PORT = 58082;
    private static final String SERVICE_URL =  "http://localhost:" + SERVICE_PORT + '/' + TransactionAwareResource.PSEGMENT;

    public static void main(String[] args) {
        String opt = (args.length == 0 ? "" : args[0]);

        // the example uses an embedded JAX-RS server for running the service that will take part in a transaction
        JaxrsServer.startServer("localhost", SERVICE_PORT);

        // get a helper for using RESTful transactions, passing in the well know resource endpoint for the transaction manager
        TxSupport txn = new TxSupport(TXN_MGR_URL);

        if ("-r".equals(opt)) {
            System.out.println("=============================================================================");
            System.out.println("Client: WAITING FOR RECOVERY IN 2 SECOND INTERVALS (FOR A MAX OF 130 SECONDS)");
            System.out.println("=============================================================================");

            for (long i = 0l; i < 130l; i += 2) {
                try {
                    // ask the service how many transactions it has committed since the VM started

                    String commitCnt = txn.httpRequest(new int[] {HttpURLConnection.HTTP_OK},
                            SERVICE_URL + "/commits", "GET", TxMediaType.PLAIN_MEDIA_TYPE, null, null);
                    String abortCnt = txn.httpRequest(new int[] {HttpURLConnection.HTTP_OK},
                            SERVICE_URL + "/aborts", "GET", TxMediaType.PLAIN_MEDIA_TYPE, null, null);

                    if (commitCnt != null && !"0".equals(commitCnt)) {
                        System.out.println("SUCCESS participant was recovered after " + (i * 2) + " seconds. Number of commits: " + commitCnt);
                        System.exit(0);
                    } else if (abortCnt != null && !"0".equals(abortCnt)) {
                        System.out.println("Partial SUCCESS participant was aborted after " + (i * 2) + " seconds. Number of aborts: " + abortCnt);
                        System.exit(0);
                    }

                    System.out.print(".");
                    Thread.sleep(2000);

                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }

            System.out.println("FAILURE participant was not recovered");
            System.exit(1);
        }

        // start a REST Atomic transaction
        txn.startTx();

        /*
         * Send two web service requests. Include the resource url for registering durable participation
         * in the transaction with the request
         */
        String serviceRequest = SERVICE_URL + "?enlistURL=" + txn.getDurableParticipantEnlistmentURI();

        String wId1 = txn.httpRequest(new int[] {HttpURLConnection.HTTP_OK}, serviceRequest, "GET", TxMediaType.PLAIN_MEDIA_TYPE, null, null);
        String wId2 = txn.httpRequest(new int[] {HttpURLConnection.HTTP_OK}, serviceRequest, "GET", TxMediaType.PLAIN_MEDIA_TYPE, null, null);

        // commit the transaction
        if ("-f".equals(opt)) {
            System.out.println("Client: Failing work load " + wId2);
            TransactionAwareResource.FAIL_COMMIT = wId2;
        }

        System.out.println("Client: Committing transaction");
        txn.commitTx();

        // the web service should have received prepare and commit requests from the transaction manager (TXN_MGR_URL)

        // shutdown the embedded JAX-RS server
        JaxrsServer.stopServer();
    }
}
