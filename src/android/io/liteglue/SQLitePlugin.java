/*
 * Copyright (c) 2012-2015, Chris Brody
 * Copyright (c) 2005-2010, Nitobi Software Inc.
 * Copyright (c) 2010, IBM Corporation
 *
 * License for this version: GPL v3 (http://www.gnu.org/licenses/gpl.txt) or commercial license.
 * Contact for commercial license: info@litehelpers.net
 */

package io.liteglue;

import android.annotation.SuppressLint;

import android.util.Log;

import java.io.File;
import java.lang.IllegalArgumentException;
import java.lang.Number;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class SQLitePlugin extends CordovaPlugin {

    /**
     * Multiple database runner map (static).
     * NOTE: no public static accessor to db (runner) map since it would not work with db threading.
     * FUTURE put DBRunner into a public class that can provide external accessor.
     */
    static ConcurrentHashMap<String, DBRunner> dbrmap = new ConcurrentHashMap<String, DBRunner>();

    /**
     * SQLiteConnector for native sqlite library access
     */
    static SQLiteConnector connector = new SQLiteConnector();

    /**
     * NOTE: Using default constructor, no explicit constructor.
     */

    @Override
    public Boolean shouldAllowRequest(String url) {
        Log.i("info", "*************** AQS URL");
        Log.i("info", "***************************************");

        if (url.startsWith("aqaq", 0) || url.startsWith("file:///aq", 0)) {
            Log.i("info", "**** url match: " + url);

            try {
                //webView.sendJavascript("aqcallback('decoded uri: " + java.net.URLDecoder.decode(url, "UTF-8")+ "')");
                //String s = java.net.URLDecoder.decode(url, "UTF-8");

                //Log.i("info", "*********** s: " + s);


                    String [] topComponents = url.split("#");
                    if (topComponents.length < 2) {
                        webView.loadUrl("javascript:aqcallback('SORRY MISSING #')");
                        return null;
                    }

                    // no longer needed, should be freed:
                    url = null;

                    String handleString = topComponents[1];

                    // no longer needed, should be freed:
                    topComponents = null;

                    String [] handleComponents = handleString.split("\\?");

                    if (handleComponents.length < 2) {
                        webView.loadUrl("javascript:aqcallback('SORRY MISSING ?')");
                        return null;
                    }

                    String parameters = handleComponents[1];

                    String [] routeComponents = handleComponents[0].split(":");
                    if (routeComponents.length < 2) {
                        webView.loadUrl("javascript:aqcallback('SORRY MISSING :')");
                        return null;
                    }

                    // no longer needed, should be freed:
                    handleComponents = null;

                    String routeParameters = routeComponents[1];

                    int routeParametersSep = routeParameters.indexOf('$');
                    if (routeParametersSep < 0) {
                        webView.loadUrl("javascript:aqcallback('SORRY MISSING $')");
                        return null;
                    }

                    String method = routeParameters.substring(0, routeParametersSep);

                    String internalParameters = routeParameters.substring(routeParametersSep + 1);

                    int internalSep = internalParameters.indexOf('@');
                    if (internalSep < 0) {
                        webView.loadUrl("javascript:aqcallback('SORRY MISSING @')");
                        return null;
                    }

                    String cbParameters = internalParameters.substring(0, internalSep);

                    String [] cbComponents = cbParameters.split("-");
                    if (cbComponents.length < 2) {
                        webView.loadUrl("javascript:aqcallback('SORRY MISSING -')");
                        return null;
                    }

                    // XXX SECURITY TODO: use code parameter to check a security code, like they do in the Cordova framework
                    //String code = internalParameters.substring(internalParameters + 1);
                    // ...

                    //webView.loadUrl("javascript:aqcallback('got components: " + routeComponents[0] + " " + me + " " + parameters + "')");

                    //manager.getHandler(routeComponents[0]).handleMessage(method, parameters, cbComponents[0], cbComponents[1]);

                String args = java.net.URLDecoder.decode(parameters, "UTF-8");

                // no longer needed, should be freed:
                parameters = null;

                //Log.i("info", "*********** op: " + op);
                Log.i("info", "*********** args: " + args);

                final String cbHandler = cbComponents[0];
                final String cbId = cbComponents[1];

                JSONArray aj = new JSONArray(args);

                // no longer needed, should be freed:
                args = null;

                Log.i("info", "*************** execute");
                Log.i("info", "***************************************");

                execute(method, aj, new CallbackContext(null, null) {
                    @Override
                    public void success(String s) {
                        Log.i("info", "*************** SUCCESS WITH STRING: " + s);
                        //webView.sendJavascript("aqcallback('" + s + "')");
                        String cbScript = "aqcallback('" + cbHandler + "', '" + cbId + "?" + s + "')";
                        Log.i("info", "send Javascript: " + cbScript);
                        webView.sendJavascript(cbScript);
                    }

                    @Override
                    public void success(JSONObject o) {
                        webView.sendJavascript("aqcallback('success with object: " + o.toString() + "')");
                    }

                    @Override
                    public void success(JSONArray a) {
                        Log.i("info", "**** SUCCESS WITH ARRAY: " + a.toString());
                        //webView.sendJavascript("aqcallback('" + java.net.URLEncoder.encode(a.toString()) + "')");
                        try {
                        String cbScript = "aqcallback('" + cbHandler + "', '" + cbId + "?" +
                            java.net.URLEncoder.encode(a.toString(), "UTF-8").replace("+", "%20") + "')";
                        Log.i("info", "send Javascript: " + cbScript);
                        webView.sendJavascript(cbScript);
                        } catch(Exception e) {}
                    }

                    @Override
                    public void error(String s) {
                        webView.sendJavascript("aqcallback('error with string: " + s + "')");
                    }

                });

            } catch(Exception e) {}
            return false;
        }

        return true;
    }


    /**
     * Executes the request and returns PluginResult.
     *
     * @param actionAsString The action to execute.
     * @param args   JSONArry of arguments for the plugin.
     * @param cbc    Callback context from Cordova API
     * @return       Whether the action was valid.
     */
    @Override
    public boolean execute(String actionAsString, JSONArray args, CallbackContext cbc) {

        Action action;
        try {
Log.i("info", "*.");
            action = Action.valueOf(actionAsString);
Log.i("info", "*.");
        } catch (IllegalArgumentException e) {
            // shouldn't ever happen
            Log.e(SQLitePlugin.class.getSimpleName(), "unexpected error", e);
            return false;
        }

        try {
Log.i("info", "*.");
            return executeAndPossiblyThrow(action, args, cbc);
        } catch (JSONException e) {
            // TODO: signal JSON problem to JS
            Log.e(SQLitePlugin.class.getSimpleName(), "unexpected error", e);
            return false;
        }
    }

    private boolean executeAndPossiblyThrow(Action action, JSONArray args, CallbackContext cbc)
            throws JSONException {

        boolean status = true;
        JSONObject o;
        String dbname;

Log.i("info", "*..");
        switch (action) {
            case open:
Log.i("info", "*...");
                o = args.getJSONObject(0);
Log.i("info", "*...");
                dbname = o.getString("name");
Log.i("info", "*...");
                // open database and start reading its queue
                this.startDatabase(dbname, o, cbc);
                break;

            case close:
                o = args.getJSONObject(0);
                dbname = o.getString("path");
                // put request in the q to close the db
                this.closeDatabase(dbname, cbc);
                break;

            case delete:
                o = args.getJSONObject(0);
                dbname = o.getString("path");

                deleteDatabase(dbname, cbc);

                break;

            case executeSqlBatch:
            case backgroundExecuteSqlBatch:
                JSONObject allargs = args.getJSONObject(0);

                JSONObject dbargs = allargs.getJSONObject("dbargs");
                dbname = dbargs.getString("dbname");

                int mylen = allargs.getInt("flen");

                JSONArray flatlist = allargs.getJSONArray("flatlist");
                int ai = 0;

                String[] queries = new String[mylen];

                // XXX TODO: currently goes through flatlist in multiple [2] passes
                for (int i = 0; i < mylen; i++) {
                    queries[i] = flatlist.getString(ai++);
                    int alen = flatlist.getInt(ai++);
                    ai += alen;
                }

                // put db query in the queue to be executed in the db thread:
                DBQuery q = new DBQuery(queries, flatlist, cbc);
                DBRunner r = dbrmap.get(dbname);
                if (r != null) {
                    try {
                        r.q.put(q); 
                    } catch(Exception e) {
                        Log.e(SQLitePlugin.class.getSimpleName(), "couldn't add to queue", e);
                        cbc.error("couldn't add to queue");
                    }
                } else {
                    cbc.error("database not open");
                }
                break;
        }

        return status;
    }

    /**
     * Clean up and close all open databases.
     */
    @Override
    public void onDestroy() {
        while (!dbrmap.isEmpty()) {
            String dbname = dbrmap.keySet().iterator().next();

            this.closeDatabaseNow(dbname);

            DBRunner r = dbrmap.get(dbname);
            try {
                // stop the db runner thread:
                r.q.put(new DBQuery());
            } catch(Exception e) {
                Log.e(SQLitePlugin.class.getSimpleName(), "couldn't stop db thread", e);
            }
            dbrmap.remove(dbname);
        }
    }

    // --------------------------------------------------------------------------
    // LOCAL METHODS
    // --------------------------------------------------------------------------

    private void startDatabase(String dbname, JSONObject options, CallbackContext cbc) {
        // TODO: is it an issue that we can orphan an existing thread?  What should we do here?
        // If we re-use the existing DBRunner it might be in the process of closing...
Log.i("info", "**.");
        DBRunner r = dbrmap.get(dbname);
Log.i("info", "**.");

        // Brody TODO: It may be better to terminate the existing db thread here & start a new one, instead.
        if (r != null) {
            // don't orphan the existing thread; just re-open the existing database.
            // In the worst case it might be in the process of closing, but even that's less serious
            // than orphaning the old DBRunner.
Log.i("info", "aa **..");
            cbc.success("a1"); // Indicate Android version with flat JSON interface
Log.i("info", "**..");
        } else {
Log.i("info", "**..");
            r = new DBRunner(dbname, options, cbc);
Log.i("info", "**..");
            dbrmap.put(dbname, r);
Log.i("info", "**..");
            this.cordova.getThreadPool().execute(r);
Log.i("info", "**..");
        }
    }
    /**
     * Open a database.
     *
     * @param dbName   The name of the database file
     */
    private SQLiteConnection openDatabase(String dbname, CallbackContext cbc) throws Exception {
        try {
            // ASSUMPTION: no db (connection/handle) is already stored in the map
            // [should be true according to the code in DBRunner.run()]

            File dbfile = this.cordova.getActivity().getDatabasePath(dbname);

            if (!dbfile.exists()) {
                dbfile.getParentFile().mkdirs();
            }

            Log.v("info", "Open sqlite db: " + dbfile.getAbsolutePath());

            SQLiteConnection mydbc = connector.newSQLiteConnection(dbfile.getAbsolutePath(),
                SQLiteOpenFlags.READWRITE | SQLiteOpenFlags.CREATE);

            // Indicate Android version with flat JSON interface
            cbc.success("a1");

            return mydbc;
        } catch (Exception e) {
            cbc.error("can't open database " + e);
            throw e;
        }
    }

    /**
     * Close a database (in another thread).
     *
     * @param dbName   The name of the database file
     */
    private void closeDatabase(String dbname, CallbackContext cbc) {
        DBRunner r = dbrmap.get(dbname);
        if (r != null) {
            try {
                r.q.put(new DBQuery(false, cbc));
            } catch(Exception e) {
                if (cbc != null) {
                    cbc.error("couldn't close database" + e);
                }
                Log.e(SQLitePlugin.class.getSimpleName(), "couldn't close database", e);
            }
        } else {
            if (cbc != null) {
                cbc.success();
            }
        }
    }

    /**
     * Close a database (in the current thread).
     *
     * @param dbname   The name of the database file
     */
    private void closeDatabaseNow(String dbname) {
        DBRunner r = dbrmap.get(dbname);

        if (r != null) {
            SQLiteConnection mydbc = r.mydbc;

            try {
                if (mydbc != null)
                    mydbc.dispose();
            } catch(Exception e) {
                Log.e(SQLitePlugin.class.getSimpleName(), "couldn't close database", e);
            }
        }
    }

    private void deleteDatabase(String dbname, CallbackContext cbc) {
        DBRunner r = dbrmap.get(dbname);
        if (r != null) {
            try {
                r.q.put(new DBQuery(true, cbc));
            } catch(Exception e) {
                if (cbc != null) {
                    cbc.error("couldn't close database" + e);
                }
                Log.e(SQLitePlugin.class.getSimpleName(), "couldn't close database", e);
            }
        } else {
            boolean deleteResult = this.deleteDatabaseNow(dbname);
            if (deleteResult) {
                cbc.success();
            } else {
                cbc.error("couldn't delete database");
            }
        }
    }

    /**
     * Delete a database.
     *
     * @param dbName   The name of the database file
     *
     * @return true if successful or false if an exception was encountered
     */
    private boolean deleteDatabaseNow(String dbname) {
        File dbfile = this.cordova.getActivity().getDatabasePath(dbname);

        try {
            return cordova.getActivity().deleteDatabase(dbfile.getAbsolutePath());
        } catch (Exception e) {
            Log.e(SQLitePlugin.class.getSimpleName(), "couldn't delete database", e);
            return false;
        }
    }

    /**
     * Executes a batch request and sends the results via cbc.
     *
     * @param mydbc      sqlite connection reference
     * @param queryarr   Array of query strings
     * @param flatlist   Flat array of JSON query statements and parameters
     * @param cbc        Callback context from Cordova API
     */
    void executeSqlBatch(SQLiteConnection mydbc, String[] queryarr, JSONArray flatlist, CallbackContext cbc) {
        if (mydbc == null) {
            // not allowed - can only happen if someone has closed (and possibly deleted) a database and then re-used the database
            cbc.error("database has been closed");
            return;
        }

        int len = queryarr.length;
        JSONArray batchResultsList = new JSONArray();

        int ai=0;

        for (int i = 0; i < len; i++) {
            int rowsAffectedCompat = 0;
            boolean needRowsAffectedCompat = false;

            try {
                String query = queryarr[i];

                ai++;
                int alen = flatlist.getInt(ai++);

                // Need to do this in case this.executeSqlStatement() throws:
                int query_ai = ai;
                ai += alen;

                this.executeSqlStatement(mydbc, query, flatlist, query_ai, alen, batchResultsList);
            } catch (Exception ex) {
                ex.printStackTrace();
                Log.e("executeSqlBatch", "SQLitePlugin.executeSql[Batch](): Error=" + ex.getMessage());
                // TODO what to do?
            }
        }

        cbc.success(batchResultsList);
    }

    private void executeSqlStatement(SQLiteConnection mydbc, String query, JSONArray paramsAsJson,
                                     int firstParamIndex, int paramCount, JSONArray batchResultsList) throws Exception {
        boolean hasRows = false;

        SQLiteStatement myStatement = null;

        long newTotal = 0;
        long rowsAffected = 0;
        long insertId = -1;

        String errorMessage = null;
        int sqlite_error_code = -1;

        try {
            myStatement = mydbc.prepareStatement(query);

            for (int i = 0; i < paramCount; ++i) {
                int jsonParamIndex = firstParamIndex + i;
                if (paramsAsJson.isNull(jsonParamIndex)) {
                    myStatement.bindNull(i + 1);
                } else {
                    Object p = paramsAsJson.get(jsonParamIndex);
                    if (p instanceof Float || p instanceof Double) 
                        myStatement.bindDouble(i + 1, paramsAsJson.getDouble(jsonParamIndex));
                    else if (p instanceof Number) 
                        myStatement.bindLong(i + 1, paramsAsJson.getLong(jsonParamIndex));
                    else
                        myStatement.bindTextNativeString(i + 1, paramsAsJson.getString(jsonParamIndex));
                }
            }

            long lastTotal = mydbc.getTotalChanges();
            hasRows = myStatement.step();

            newTotal = mydbc.getTotalChanges();
            rowsAffected = newTotal - lastTotal;

            if (rowsAffected > 0)
                insertId = mydbc.getLastInsertRowid();

        } catch (java.sql.SQLException ex) {
            ex.printStackTrace();
            sqlite_error_code= ex.getErrorCode();
            errorMessage = ex.getMessage();
            Log.e("executeSqlBatch", "SQLitePlugin.executeSql[Batch](): sqlite error code: " + sqlite_error_code + " message: " + errorMessage);
        } catch (Exception ex) {
            ex.printStackTrace();
            errorMessage = ex.getMessage();
            Log.e("executeSqlBatch", "SQLitePlugin.executeSql[Batch](): Error=" + errorMessage);
        }

        // If query result has rows
        if (hasRows) {
            String key = "";
            int colCount = myStatement.getColumnCount();

            // XXX ASSUMPTION: in this case insertId & rowsAffected would not apply here
            batchResultsList.put("okrows");

            // Build up JSON result object for each row
            do {
                try {
                    batchResultsList.put(colCount);

                    for (int i = 0; i < colCount; ++i) {
                        key = myStatement.getColumnName(i);
                        batchResultsList.put(key);

                        switch (myStatement.getColumnType(i)) {
                        case SQLColumnType.NULL:
                            batchResultsList.put(JSONObject.NULL);
                            break;

                        case SQLColumnType.REAL:
                            batchResultsList.put(myStatement.getColumnDouble(i));
                            break;

                        case SQLColumnType.INTEGER:
                            batchResultsList.put(myStatement.getColumnLong(i));
                            break;

                        // For TEXT & BLOB:
                        default:
                            batchResultsList.put(myStatement.getColumnTextNativeString(i));
                        }

                    }

                } catch (JSONException e) {
                    e.printStackTrace();
                    // TODO what to do?
                }
            } while (myStatement.step());

            batchResultsList.put("endrows");
        } else if (errorMessage != null) {
            batchResultsList.put("error");
            switch (sqlite_error_code) {
            case SQLCode.ERROR:
                batchResultsList.put(5); // SQLException.SYNTAX_ERR
                break;
            case 13: // SQLITE_FULL
                batchResultsList.put(4); // SQLException.QUOTA_ERR
                break;
            case 19: // SQLITE_CONSTRAINT
                batchResultsList.put(6); // SQLException.CONSTRAINT_ERR
                break;
            default:
                batchResultsList.put(0); // SQLException.UNKNOWN_ERR
            }
            batchResultsList.put(sqlite_error_code);
            batchResultsList.put(errorMessage);
        } else if (rowsAffected > 0) {
            batchResultsList.put("ch2");
            batchResultsList.put(rowsAffected);
            batchResultsList.put(insertId);
        } else {
            batchResultsList.put("ok");
        }

        if (myStatement != null) myStatement.dispose();
    }

    private class DBRunner implements Runnable {
        final String dbname;

        final BlockingQueue<DBQuery> q;
        final CallbackContext openCbc;

        SQLiteConnection mydbc;

        DBRunner(final String dbname, JSONObject options, CallbackContext cbc) {
            this.dbname = dbname;

            this.q = new LinkedBlockingQueue<DBQuery>();
            this.openCbc = cbc;
        }

        public void run() {
            try {
                this.mydbc = openDatabase(dbname, this.openCbc);
            } catch (Exception e) {
                Log.e(SQLitePlugin.class.getSimpleName(), "unexpected error, stopping db thread", e);
                dbrmap.remove(dbname);
                return;
            }

            DBQuery dbq = null;

            try {
                dbq = q.take();

                while (!dbq.stop) {
                    executeSqlBatch(mydbc, dbq.queries, dbq.flatlist, dbq.cbc);

                    dbq = q.take();
                }
            } catch (Exception e) {
                Log.e(SQLitePlugin.class.getSimpleName(), "unexpected error", e);
            }

            if (dbq != null && dbq.close) {
                try {
                    closeDatabaseNow(dbname);

                    dbrmap.remove(dbname); // (should) remove ourself

                    if (!dbq.delete) {
                        dbq.cbc.success();
                    } else {
                        try {
                            boolean deleteResult = deleteDatabaseNow(dbname);
                            if (deleteResult) {
                                dbq.cbc.success();
                            } else {
                                dbq.cbc.error("couldn't delete database");
                            }
                        } catch (Exception e) {
                            Log.e(SQLitePlugin.class.getSimpleName(), "couldn't delete database", e);
                            dbq.cbc.error("couldn't delete database: " + e);
                        }
                    }                    
                } catch (Exception e) {
                    Log.e(SQLitePlugin.class.getSimpleName(), "couldn't close database", e);
                    if (dbq.cbc != null) {
                        dbq.cbc.error("couldn't close database: " + e);
                    }
                }
            }
        }
    }

    private final class DBQuery {
        // XXX TODO replace with DBRunner action enum:
        final boolean stop;
        final boolean close;
        final boolean delete;
        final String[] queries;
        final JSONArray flatlist;
        final CallbackContext cbc;

        DBQuery(String[] myqueries, JSONArray flatlist, CallbackContext c) {
            this.stop = false;
            this.close = false;
            this.delete = false;
            this.queries = myqueries;
            this.flatlist = flatlist;
            this.cbc = c;
        }

        DBQuery(boolean delete, CallbackContext cbc) {
            this.stop = true;
            this.close = true;
            this.delete = delete;
            this.queries = null;
            this.flatlist = null;
            this.cbc = cbc;
        }

        // signal the DBRunner thread to stop:
        DBQuery() {
            this.stop = true;
            this.close = false;
            this.delete = false;
            this.queries = null;
            this.flatlist = null;
            this.cbc = null;
        }
    }

    private static enum Action {
        open,
        close,
        delete,
        executeSqlBatch,
        backgroundExecuteSqlBatch,
    }
}

/* vim: set expandtab : */
