package com.codewithArchiel;


import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;
import java.util.HashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


@WebServlet("/ussd")
public class Ussd extends HttpServlet {

    Logger logger = LogManager.getLogger(Ussd.class);
    private final Map<String, String> sessionStates = new HashMap<>();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            //Tolerar melhor o fetch dos gets.
            //numero, nao verifiquei o log para ver se o regista o numero (regra de negocio de como faz a validacao para fetch na DB)
            String msisdn = req.getParameter("MSISDN") == null ? "": req.getParameter("MSISDN");
            //comando inserido pelo usuario
            String ussdCommand = req.getParameter("UssdString") != null ? req.getParameter("UssdString") : "";
            //nao sei se gera
            String uniqueId = req.getParameter("UniqueId") != null ? req.getParameter("UniqueId") :"";
            //Vem como null e e manipulado internamente como se fosse variavel
            String isnewrequest = req.getParameter("isnewrequest") != null ? req.getParameter("isnewrequest"): "";
            //Existem outros parametros que nao foram incluidos


            //TODO receber todos parametros nao e mandatorio
            // isnewrequest serve para gerir menus, e validar o tipo de entrada existe um trecho de codigo onde concatena o usstring:  * ussdString # (descobrir o porque)
            // O header responsavel por manter a sessao e o MsgType e nao Freeflow nem isnewrequest nem sMsType como indica a documentacao
            // acredito que usou-se nomes ficticios como metodo de seguranca
            // Gerir melhor a sessao, utilizando biblioteca HttpServlet*


            String sessionKey = msisdn + uniqueId;


            // Determine current state
            String currentState = sessionStates.getOrDefault(sessionKey, "menu");
            String responseText;
            boolean isContinue;

            switch (currentState) {
                case "menu":
                    resp.addHeader("MsgType", "FC");//Header responsavel por manter a sessao a cada iteracao, (redundante o add e set)
                    resp.setHeader ("MsgType", "FC");// deve-se devolver o header a cada iteracao, que queira-se manter a sessao
                    responseText = "Welcome to MiniMenu:\n1. Balance\n2. Data Usage\n3. Exit";
                    sessionStates.put(sessionKey, "awaiting-selection");
                    logger.info("MSISDN: "+ msisdn + " UniqueId: "+ uniqueId + " SessionKey: "+ sessionKey+", Header MsgType: "+resp.getHeader("MsgType"));
                    isContinue = true;
                    break;

                case "awaiting-selection":
                    if ("1".equals(ussdCommand)) {
                        responseText = "Your balance is $42.00. Thanks!";
                        //metodo de envio do header a FB definido abaixo
                        sessionStates.remove(sessionKey);
                        logger.info("MSISDN: "+ msisdn + " UniqueId: "+ uniqueId + " SessionKey: "+ sessionKey+", Header MsgType: "+resp.getHeader("MsgType"));
                        isContinue = false;
                    } else if ("2".equals(ussdCommand)) {
                        responseText = "You have used 1.2GB of your 5GB plan.";
                        //metodo de envio do header a FB definido abaixo
                        sessionStates.remove(sessionKey);
                        logger.info("MSISDN: "+ msisdn + " UniqueId: "+ uniqueId + " SessionKey: "+ sessionKey+", Header MsgType: "+resp.getHeader("MsgType"));
                        isContinue = false;
                    } else if ("3".equals(ussdCommand)) {
                        responseText = "Goodbye!";
                        //metodo de envio do header a FB definido abaixo
                        sessionStates.remove(sessionKey);
                        logger.info("MSISDN: "+ msisdn + " UniqueId: "+ uniqueId + " SessionKey: "+ sessionKey+", Header MsgType: "+resp.getHeader("MsgType"));
                        isContinue = false;
                    } else {
                        responseText = "Invalid option. Please try:\n1. Balance\n2. Data Usage\n3. Exit";
                        sessionStates.put(sessionKey, "awaiting-selection");
                        isContinue = true;
                    }
                    break;

                default:
                    responseText = "Welcome to MiniMenu:\n1. Balance\n2. Data Usage\n3. Exit";
                    sessionStates.put(sessionKey, "awaiting-selection");
                    isContinue = true;
                    break;
            }


            String wrappedText = wrap(responseText, isContinue);
            //nao altera porque defini o content type como text, ele converte e envia String de bytes, pode-se remover
            byte[] responseBytes = wrappedText.getBytes("UTF-8");

            //resp.setStatus(HttpServletResponse.SC_OK); a operadora podia ter pedido resposta do servlet, nao e o caso
            resp.setContentType("text/plain"); //podia ter pedido Html, XMl nao foi o caso

            //Este metodo de fazer set do headers
            resp.setHeader("MsgType", isContinue ? "FC" : "FB");
            resp.setHeader("Expires", "-1");

            resp.setContentLength(responseBytes.length);// define o tamanho da String que a operadora ira receber a cada iteracao

            PrintWriter out = resp.getWriter();//...
            out.write(wrappedText);//pensou se que fosse uma keyword que envolve-se a String enviada a operadora que permitia a sessao ser mantida
            out.flush();

        } catch (Exception e) {
            logger.error("### Error processing USSD request ###", e);
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Unexpected USSD error");
        }
    }


    //Ja que descobriu-se a causa, nao e necessario. Mas mantive somente para ver-se uma forma de forcar
    private String wrap(String message, boolean isContinue) {
        String keyword = isContinue ? "continue" : "break";
        return keyword + "\n" + message + "\n" + keyword;
    }
}

