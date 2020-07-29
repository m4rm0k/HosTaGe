package de.tudarmstadt.informatik.hostage.protocol.coapUtils;

import com.mbed.coap.exception.CoapCodeException;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.Code;
import com.mbed.coap.server.CoapExchange;
import com.mbed.coap.utils.CoapResource;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Objects;
import java.util.UUID;

import de.tudarmstadt.informatik.hostage.Hostage;
import de.tudarmstadt.informatik.hostage.commons.HelperUtils;
import de.tudarmstadt.informatik.hostage.commons.SubnetUtils;
import de.tudarmstadt.informatik.hostage.logging.AttackRecord;
import de.tudarmstadt.informatik.hostage.logging.MessageRecord;
import de.tudarmstadt.informatik.hostage.logging.SyncDevice;
import de.tudarmstadt.informatik.hostage.protocol.Protocol;

public class COAPHandler extends CoapResource {
    private static ArrayList<CoapPacket> requests = new ArrayList<>();
    private static final ArrayList<CoapPacket> fullRequests = new ArrayList<>();
    private String value="Response";


    public COAPHandler(){

    }

    public COAPHandler(String value){
        this.value=value;
    }


    @Override
    public void get(CoapExchange exchange) {
        requests.add(exchange.getRequest());
        fullRequests.add(exchange.getRequest());
        exchange.setResponseCode(Code.C205_CONTENT);
        exchange.setResponseBody(value);
        exchange.sendResponse();
    }

    @Override
    public void put(CoapExchange exchange) {
        requests.add(exchange.getRequest());
        fullRequests.add(exchange.getRequest());
        exchange.setResponseCode(Code.C204_CHANGED);
        exchange.setResponseBody(Integer.valueOf(exchange.getRequestBody().length).toString());
        exchange.sendResponse();
    }

    @Override
    public void post(CoapExchange exchange) throws CoapCodeException {
        requests.add(exchange.getRequest());
        fullRequests.add(exchange.getRequest());
        throw new CoapCodeException(Code.C400_BAD_REQUEST);
    }

    @Override
    public void delete(CoapExchange exchange){
        requests.add(exchange.getRequest());
        fullRequests.add(exchange.getRequest());
        exchange.setResponseCode(Code.C202_DELETED);
        exchange.sendResponse();
    }

    public static boolean isAnAttackOngoing(){
        return !requests.isEmpty();
    }

    private static CoapPacket getCurrentPacket(){
        InetSocketAddress socketAddress = new InetSocketAddress(5683);
        CoapPacket coapPacket = new CoapPacket(socketAddress);
        if(!requests.isEmpty())
            return requests.get(0);
        else
            return coapPacket;
    }

    public static void removeCurrentConnected(){
        if(!requests.isEmpty())
            requests.clear();
    }

    /**
     * Helper method for Handler, creates an attackRecord with the logs from the InterceptHandler.
     * @param attack_id
     * @param externalIP
     * @param protocol
     * @param subnetMask
     * @param BSSID
     * @param internalIPAddress
     * @return
     * @throws UnknownHostException
     */

    public synchronized static AttackRecord createAttackRecord(Long attack_id, String externalIP, Protocol protocol, int subnetMask, String BSSID, int internalIPAddress){
        AttackRecord record = new AttackRecord();
        String internalIp = HelperUtils.intToStringIp(internalIPAddress);
        String remoteIp = getCurrentPacket().getRemoteAddress().getAddress().toString();
        remoteIp = remoteIp.startsWith("/") ? remoteIp.substring(1) : remoteIp;

        record.setAttack_id(attack_id);
        record.setSync_id(attack_id);
        if(SyncDevice.currentDevice()!=null)
            record.setDevice(Objects.requireNonNull(SyncDevice.currentDevice()).getDeviceID());
        else
            record.setDevice(UUID.randomUUID().toString());
        record.setProtocol("COAP");
        record.setExternalIP(externalIP);
        record.setLocalIP(internalIp);
        record.setLocalPort(5683);
        int remoteIPAddress = 0;
        try {
            remoteIPAddress = HelperUtils.getInetAddress(InetAddress.getByName(remoteIp));
        } catch (UnknownHostException e) {
            remoteIPAddress=0;
            e.printStackTrace();
        }
        record.setWasInternalAttack(checkIfIsInternalAttack(remoteIPAddress,internalIp));
        record.setRemoteIP(remoteIp);
        record.setRemotePort(getCurrentPacket().getRemoteAddress().getPort());
        record.setBssid(BSSID);

        return record;
    }

    private synchronized static boolean checkIfIsInternalAttack(int remoteIPAddress,String internalIPAddress){
        int prefix = Hostage.prefix;
        SubnetUtils utils = new SubnetUtils(internalIPAddress+"/"+prefix);
        String remoteIP = HelperUtils.intToStringIp(remoteIPAddress);

        return utils.getInfo().isInRange(remoteIP);
    }

    /**
     *Helper method for Handler, creates a messageRecord with the logs from the COAPHandler.
     * @param type
     * @param attack_id
     * @return
     */
    public synchronized static MessageRecord createMessageRecord(MessageRecord.TYPE type, long attack_id) {
        MessageRecord record = new MessageRecord(true);
        record.setAttack_id(attack_id);
        record.setType(type);
        record.setStringMessageType(type.name());
        record.setTimestamp(System.currentTimeMillis());
        record.setPacket(getCurrentPacket().getPayloadString()+" "+"Message Id: "+getCurrentPacket().getMessageId());
        return record;
    }

}