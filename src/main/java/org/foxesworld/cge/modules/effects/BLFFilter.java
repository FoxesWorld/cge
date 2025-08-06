package org.foxesworld.cge.modules.effects;

import com.jme3.app.Application;
import com.jme3.asset.AssetManager;
import com.jme3.export.InputCapsule;
import com.jme3.export.JmeExporter;
import com.jme3.export.JmeImporter;
import com.jme3.export.OutputCapsule;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.post.Filter;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;

import java.io.IOException;

public class BLFFilter extends Filter {
    private float DEFAULT_DURATION=1.0f;
    private float DEFAULT_BLOOM_STRENGTH=0.5f;
    private float DEFAULT_BLOOM_STRENGTH_DYNAMIC=1f;
    private float DEFAULT_BLOOM_RANGE=1.5f;

    private float DEFAULT_ANAM_STRENGTH=0.5f;
    private float DEFAULT_ANAM_STRENGTH_DYNAMIC=1f;
    private float DEFAULT_ANAM_RANGE=1.5f;

    private float DEFAULT_STREAKS_STRENGTH_DYNAMIC=0;

    private Material material;

    private float duration = DEFAULT_DURATION;


    private boolean enabled = false;
    private boolean bloomFadeOut = false;
    private boolean enabledFakeBloom = false;
    private boolean enabledGhosts = false;
    private boolean enabledDistortion = false;
    private boolean enabledAnamorphic = false;
    private float startTime=0;

    private float bloomStrength=DEFAULT_BLOOM_STRENGTH;//=0.5;
    private float bloomStrengthDynamic=DEFAULT_BLOOM_STRENGTH_DYNAMIC;//=0.1;
    private float bloomRange=DEFAULT_BLOOM_RANGE;// = 1.5  ;


    private float anamStrength=DEFAULT_ANAM_STRENGTH;//=0.5;
    private float anamStrengthDynamic=DEFAULT_ANAM_STRENGTH_DYNAMIC;//=0.1;
    private float anamRange=DEFAULT_ANAM_RANGE;// = 1.5  ;
    private int DEFAULT_STREAKS_COUNT=7;

    private float DEFAULT_STREAKS_LENGTH=0.5f;
    private boolean enabledStreaks= false;
    private int streaksType;
    private float  streaksCount=DEFAULT_STREAKS_COUNT;
    private float streaksLength=DEFAULT_STREAKS_LENGTH;
    private float streaksStrengthDynamic=DEFAULT_STREAKS_STRENGTH_DYNAMIC;

    private  ColorRGBA colorBloom=new ColorRGBA(1.0f, 0.5f, 0.25f,1.f);
    private  ColorRGBA colorAnam=new ColorRGBA(1.0f, 0.5f, 0.25f,1.f);
    private  ColorRGBA colorGhosts=new ColorRGBA(1.0f, 0.5f, 0.25f,1.f);
    private  ColorRGBA colorStreaks=new ColorRGBA(1.0f, 0.5f, 0.25f,1.f);

    //private float timeOfWork=0;
    private Vector2f clickPoint=new Vector2f(0,0);
    private Vector2f resolution=new Vector2f(0,0);
    Application app;
    public BLFFilter(Application app) {
        super("BLFFilter");
        this.app=app;
    }


    @Override
    protected void initFilter(AssetManager assetManager, RenderManager arg1, ViewPort arg2, int w, int h) {
        resolution.set(w,h);
        material = new Material(assetManager, "assets/MatDefs/LensFlareCinematic.j3md");
        material.setBoolean("Enabled", enabled);
        material.setVector2("Resolution", resolution);

//Bloom
        material.setFloat("Duration", duration);
        material.setFloat("StartTime", startTime);
        material.setBoolean("BloomFadeOut", bloomFadeOut);
        material.setBoolean("EnabledFakeBloom", enabledFakeBloom);
        material.setFloat("BloomStrength", bloomStrength);
        material.setFloat("BloomRange", bloomRange);
        material.setFloat("BloomStrengthDynamic", bloomStrengthDynamic);
        material.setColor("ColorBloom", colorBloom);

//Anamorphic
        material.setBoolean("EnabledAnamorphic", enabledAnamorphic);
        material.setFloat("AnamStrength", anamStrength);
        material.setFloat("AnamRange", anamRange);
        material.setFloat("AnamStrengthDynamic", anamStrengthDynamic);
        material.setColor("ColorAnam", colorAnam);

//Ghosts
        material.setBoolean("EnabledGhosts", enabledGhosts);
        material.setBoolean("EnabledDistortion", enabledDistortion);
        material.setColor("ColorGhosts", colorGhosts);

//Streaks
        material.setColor("ColorStreaks", colorStreaks);
        material.setFloat("StreaksLength", streaksLength);
        material.setFloat("StreaksCount", streaksCount);
        material.setInt("StreaksType", streaksType);
        material.setBoolean("EnabledStreaks", enabledStreaks);

    }


    @Override
    protected Material getMaterial() {
        return material;
    }

    /**

     The duration. A
     @param duration
     */
    public void setDuration(float duration) {
//checkFloatArgument(softness, 0, 1, "softness");
        this.duration = duration;
        if(material!=null)
            material.setFloat("Duration", duration);
    }
    public float getDuration() {
        return duration;
    }


    public boolean getBloomFadeOut() {
        return bloomFadeOut;
    }

    public void startStartTime(  )
    {
        //
        float timeSinceStart =  app.getTimer().getTimeInSeconds();
        material.setFloat("StartTime", timeSinceStart);

    }
    public void setEnabledEffect(boolean enabled) {
        //  super.setEnabled(enabled);
        this.enabled=enabled;
        // System.out.println("DDDD="+enabled);
        //
        if(material!=null)
            material.setBoolean("Enabled", enabled);
    }
    public void setEnabledFakeBloom(boolean enabledFakeBloom) {
        this.enabledFakeBloom=enabledFakeBloom;
        //
        if(material!=null)
            material.setBoolean("EnabledFakeBloom", enabledFakeBloom);
    }
    public void setEnabledGhosts(boolean enabledGhosts) {
        this.enabledGhosts=enabledGhosts;
        //
        if(material!=null)
            material.setBoolean("EnabledGhosts", enabledGhosts);
    }



    public void setBloomFadeOut(boolean bloomFadeOut) {
        this.bloomFadeOut=bloomFadeOut;
        //
        material.setBoolean("BloomFadeOut", bloomFadeOut);
    }

    public boolean isBloomFadeOut( )
    {
        return bloomFadeOut;
    }

    public void setClickPoint(Vector2f clickPoint ) {
        material.setVector2("ClickPoint", clickPoint);
    }


    @Override
    public void write(JmeExporter ex) throws  IOException {
        super.write(ex);
        OutputCapsule oc = ex.getCapsule(this);
        oc.write(duration, "Duration", DEFAULT_DURATION);
        oc.write(startTime, "StartTime", 0);
        oc.write(bloomFadeOut, "BloomFadeOut", false);
        oc.write(enabledFakeBloom, "EnabledFakeBloom", false);
        oc.write(bloomStrength, "BloomStrength", 0f);
        oc.write(bloomRange, "BloomRange", 0f);
        oc.write(bloomStrengthDynamic, "BloomStrengthDynamic", 0f);
        oc.write(colorBloom, "ColorBloom", ColorRGBA.Blue);

        oc.write(enabledAnamorphic, "EnabledAnamorphic", false);
        oc.write(anamStrength, "AnamStrength", 0f);
        oc.write(anamRange, "AnamRange", 0f);
        oc.write(anamStrengthDynamic, "AnamStrengthDynamic", 0f);
        oc.write(colorAnam, "ColorAnam", ColorRGBA.Blue);

        oc.write(enabledGhosts, "EnabledAnamorphic", false);
        oc.write(enabledDistortion, "EnabledAnamorphic", false);
        oc.write(colorGhosts, "ColorAnam", ColorRGBA.Blue);

        oc.write(enabledStreaks, "EnabledStreaks", false);
        oc.write(streaksLength, "StreaksLength", 0f);
        oc.write(streaksCount, "StreaksCount", 0f);
        oc.write(streaksType, "StreaksType", 0 );
        oc.write(colorStreaks, "ColorStreaks", ColorRGBA.Blue);
    }

    @Override
    public void read(JmeImporter im) throws IOException {
        super.read(im);
        InputCapsule ic = im.getCapsule(this);
        duration = ic.readFloat("Duration", DEFAULT_DURATION);
        startTime = ic.readFloat("StartTime", 0);
        bloomFadeOut = ic.readBoolean("BloomFadeOut", false);
        enabledFakeBloom = ic.readBoolean("EnabledFakeBloom", false);
        bloomStrength = ic.readFloat("BloomStrength", 0);
        bloomRange = ic.readFloat("BloomRange", 0);
        bloomStrengthDynamic = ic.readFloat("BloomStrengthDynamic", 0);

        enabledAnamorphic = ic.readBoolean("EnabledAnamorphic", false);
        anamStrength = ic.readFloat("AnamStrength", 0);
        anamRange = ic.readFloat("AnamRange", 0);
        anamStrengthDynamic = ic.readFloat("AnamStrengthDynamic", 0);

        enabledGhosts = ic.readBoolean("EnabledAnamorphic", false);
        enabledDistortion = ic.readBoolean("EnabledAnamorphic", false);

        enabledStreaks = ic.readBoolean("EnabledStreaks", false);
        streaksLength = ic.readFloat("StreaksLength", 0);
        streaksCount = ic.readFloat("StreaksCount", 0);
        streaksType = ic.readInt("StreaksType", 0);
    }



    public float getBloomStrength() {
        return bloomStrength;
    }

    public void setBloomStrength(float bloomStrength) {
        checkFloatArgument(bloomStrength, 0, 5, "BloomStrength");
        this.bloomStrength = bloomStrength;
        if(material!=null)
            material.setFloat("BloomStrength", bloomStrength);
    }

    public float getBloomStrengthDynamic() {
        return bloomStrengthDynamic;
    }

    public void setBloomStrengthDynamic(float bloomStrengthDynamic) {
        this.bloomStrengthDynamic = bloomStrengthDynamic;
        if(material!=null)
            material.setFloat("BloomStrengthDynamic", bloomStrengthDynamic);
    }

    public float getBloomRange() {
        return bloomRange;
    }

    public void setBloomRange(float bloomRange) {
        checkFloatArgument(bloomRange, 0, 15, "BloomRange");
        this.bloomRange = bloomRange;
        if(material!=null)
            material.setFloat("BloomRange", bloomRange);
    }

    public boolean isEnabledAnamorphic() {
        return enabledAnamorphic;
    }

    public void setEnabledAnamorphic(boolean enabledAnamorphic) {
        this.enabledAnamorphic=enabledAnamorphic;
        if(material!=null)
            material.setBoolean("EnabledAnamorphic", enabledAnamorphic);

    }

    public float getAnamStrength() {
        return anamStrength;
    }

    public void setAnamStrength(float anamStrength) {
        checkFloatArgument(anamStrength, 0, 5, "AnamStrength");
        this.anamStrength = anamStrength;
        if(material!=null)
            material.setFloat("AnamStrength", anamStrength);
    }

    public float getAnamStrengthDynamic() {
        return anamStrengthDynamic;
    }

    public void setAnamStrengthDynamic(float anamStrengthDynamic) {
        this.anamStrengthDynamic = anamStrengthDynamic;
        if(material!=null)
            material.setFloat("AnamStrengthDynamic", anamStrengthDynamic);
    }

    public float getAnamRange() {
        return anamRange;
    }

    public void setAnamRange(float anamRange) {
        checkFloatArgument(anamRange, 0, 15, "AnamRange");
        this.anamRange = anamRange;
        if(material!=null)
            material.setFloat("AnamRange", anamRange);
    }

    public boolean isEnabledDistortion() {
        return enabledDistortion;
    }

    public void setEnabledDistortion(boolean enabledDistortion) {
        this.enabledDistortion = enabledDistortion;
        if(material!=null)
            material.setBoolean("EnabledDistortion", enabledDistortion);
    }


    public ColorRGBA getColorBloom() {
        return colorBloom;
    }

    public void setColorBloom(ColorRGBA colorBloom) {
        this.colorBloom = colorBloom;
        if(material!=null)
            material.setColor("ColorBloom", colorBloom);
    }

    public ColorRGBA getColorAnam() {
        return colorAnam;
    }

    public void setColorAnam(ColorRGBA colorAnam) {
        this.colorAnam = colorAnam;
        if(material!=null)
            material.setColor("ColorAnam", colorAnam);
    }

    public ColorRGBA getColorGhosts() {
        return colorGhosts;
    }

    public void setColorGhosts(ColorRGBA colorGhosts) {
        this.colorGhosts = colorGhosts;
        if(material!=null)
            material.setColor("ColorGhosts", colorGhosts);
    }

    public boolean isEnabledStreaks() {
        return enabledStreaks;
    }

    public void setEnabledStreaks(boolean enabledStreaks) {
        this.enabledStreaks = enabledStreaks;
        if(material!=null)
            material.setBoolean("EnabledStreaks", enabledStreaks);
    }

    public int getStreaksType() {
        return streaksType;

    }

    public void setStreaksType(int streaksType) {
        checkFloatArgument(streaksType, 0, 1, "StreaksType");
        this.streaksType = streaksType;
        if(material!=null)
            material.setInt("StreaksType", streaksType);
    }

    public float getStreaksCount() {
        return streaksCount;
    }

    public void setStreaksCount(float streaksCount) {
        checkIntArgument((int)streaksCount,3, 50, "StreaksCount");
        this.streaksCount = streaksCount;
        if(material!=null)
            material.setFloat("StreaksCount", streaksCount);
    }

    public float getStreaksLength() {
        return streaksLength;
    }

    public void setStreaksLength(float streaksLength) {
        checkFloatArgument( streaksLength, 0, 5, "StreaksLength");
        this.streaksLength = streaksLength;
        if(material!=null)
            material.setFloat("StreaksLength", streaksLength);
    }

    public ColorRGBA getColorStreaks() {
        return colorStreaks;
    }

    public void setColorStreaks(ColorRGBA colorStreaks) {
        this.colorStreaks = colorStreaks;
        if(material!=null)
            material.setColor("ColorStreaks", colorStreaks);
    }

    public float getStreaksStrengthDynamic() {
        return streaksStrengthDynamic;
    }

    public void setStreaksStrengthDynamic(float streaksStrengthDynamic) {
        this.streaksStrengthDynamic = streaksStrengthDynamic;
        if(material!=null)
            material.setFloat("StreaksStrengthDynamic", streaksStrengthDynamic);
    }



    private   void checkFloatArgument(float value, float min, float max, String name) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(name + " was " + value + " but should be between " + min + " and " + max);
        }
    }
    private   void checkIntArgument(int value, int min, int max, String name) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(name + " was " + value + " but should be between " + min + " and " + max);
        }
    }
}