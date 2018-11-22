package com.demgames.polypong;

import java.util.ArrayList;
import java.util.Arrays;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.PolygonRegion;
import com.badlogic.gdx.graphics.g2d.PolygonSprite;
import com.badlogic.gdx.graphics.g2d.PolygonSpriteBatch;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Polygon;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.Box2DDebugRenderer;
import com.badlogic.gdx.physics.box2d.CircleShape;
import com.badlogic.gdx.physics.box2d.Contact;
import com.badlogic.gdx.physics.box2d.ContactImpulse;
import com.badlogic.gdx.physics.box2d.ContactListener;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.badlogic.gdx.physics.box2d.Manifold;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.math.MathUtils;

public class ClassicGame extends ApplicationAdapter{
    //use of private etc is not consistently done
    private IGlobals globalVariables;

    //setup global variables
    public ClassicGame(IGlobals globalVariables_ ) {
        this.globalVariables=globalVariables_;
    }

    //declare renderer and world related stuff
    private SpriteBatch spriteBatch;
    private PolygonSpriteBatch polygonSpriteBatch;
    private BitmapFont font;
    private float width, height;
    private ShapeRenderer shapeRenderer;
    private World world;
    private Box2DDebugRenderer debugRenderer;
    private Matrix4 debugMatrix;
    private OrthographicCamera camera;

    //declare global gamefield class
    GameField gameField;

    //define category and mask bits to decide which bodies collide with what
    final short CATEGORY_BORDER = 0x0001;
    final short CATEGORY_BALL = 0x0002;
    final short CATEGORY_BAT = 0x0004;
    final short CATEGORY_FIELDLINE = 0x0008;
    final short MASK_BORDER= CATEGORY_BALL | CATEGORY_BAT;
    final short MASK_BALL = CATEGORY_BORDER | CATEGORY_BALL | CATEGORY_BAT;
    final short MASK_BAT = CATEGORY_BORDER | CATEGORY_BALL | CATEGORY_BAT | CATEGORY_FIELDLINE;
    final short MASK_FIELDLINE  = CATEGORY_BAT;

    //global classes for balls, bats and arraylists for spriteBatch sending
    private Ball[] balls;
    private Bat[] bats;
    private ArrayList<Integer> sendBallKineticsAL=new ArrayList<Integer>(Arrays.asList(new Integer[]{}));
    private ArrayList<Integer> sendFieldChangeBallsAL =new ArrayList<Integer>(Arrays.asList(new Integer[]{}));
    private ArrayList<Integer> sendFieldChangePlayersAL =new ArrayList<Integer>(Arrays.asList(new Integer[]{}));
    private ArrayList<Integer> sendGoalAL =new ArrayList<Integer>(Arrays.asList(new Integer[]{}));

    //global sendclasses
    private IGlobals.SendVariables.SendBallKinetics sendBallKinetics=new IGlobals.SendVariables.SendBallKinetics();
    private IGlobals.SendVariables.SendBallScreenChange sendBallScreenChange=new IGlobals.SendVariables.SendBallScreenChange();
    private IGlobals.SendVariables.SendBallGoal sendBallGoal=new IGlobals.SendVariables.SendBallGoal();
    private IGlobals.SendVariables.SendBat sendBat=new IGlobals.SendVariables.SendBat();
    private IGlobals.SendVariables.SendScore sendScore=new IGlobals.SendVariables.SendScore();
    private IGlobals.SendVariables.SendConnectionState sendConnectionState=new IGlobals.SendVariables.SendConnectionState();


    //class for touch input and gestures
    Touches touches;

    //ratio of box2d physics simulation in meters to displayed pixels, when -> box2d 1/PIXEL_TO_METERS else other way round
    private final float PIXELS_TO_METERS = 100f;

    //global zoomlevel
    private float zoomLevel=1;

    //stuff for potential use
    private long frameNumber=0;
    private int sendFrameSkip=1;
    private long currentMillis=System.currentTimeMillis();

    @Override
    public void create() {
        Gdx.app.setLogLevel(Application.LOG_DEBUG);

        //get width and height of display
        width = globalVariables.getGameVariables().width;
        height = globalVariables.getGameVariables().height;

        //set fov of camera to display
        camera = new OrthographicCamera(width, height);

        //set position to middle of normal screen
        camera.position.set(0, -height/2, 0);
        camera.update();

        //copy camera to debugmatrix for synchronous displaying of elements
        debugMatrix=new Matrix4(camera.combined);
        debugMatrix.scale(PIXELS_TO_METERS, PIXELS_TO_METERS,1);
        debugRenderer=new Box2DDebugRenderer();
        //shaperenderer for rendering shapes duuh
        shapeRenderer = new ShapeRenderer();
        //set font and spriteBatch for drawing fonts and textures
        spriteBatch = new SpriteBatch();
        polygonSpriteBatch = new PolygonSpriteBatch();
        font = new BitmapFont();
        font.setColor(Color.WHITE);
        font.getData().setScale(2f);

        world=new World(new Vector2(0f,0f),true);

        //only to finger gestures
        touches=new Touches(2);

        gameField=new GameField();

        //initialize balls currently at beginning always on serverside -> player 0
        balls=new Ball[globalVariables.getGameVariables().numberOfBalls];
        for(int i=0;i<balls.length;i++) {
            balls[i]= new Ball(new Vector2(globalVariables.getGameVariables().ballsPositions[i].cpy().scl(1/PIXELS_TO_METERS)),(1+globalVariables.getGameVariables().ballsSizes[i])*width/50/PIXELS_TO_METERS,
                    i,0,10);
            //Gdx.app.debug("ClassicGame", "setup ball " + Integer.toString(i) + " on field "+ Integer.toString(globalVariables.getGameVariables().ballsPlayerScreens[i]));
        }
        //TODO positions at beginning on discoveryClient not updating

        bats= new Bat[globalVariables.getSettingsVariables().numberOfPlayers];
        for(int i=0;i<bats.length;i++) {
            bats[i]=new Bat(i);
        }

        world.setContactListener(new ContactListener() {
            @Override
            public void beginContact(Contact contact) {

            }

            @Override
            public void endContact(Contact contact) {
            }

            @Override
            public void preSolve(Contact contact, Manifold oldManifold) {
            }

            @Override
            public void postSolve(Contact contact, ContactImpulse impulse) {
            }
        });

        globalVariables.getSettingsVariables().clientConnectionStates[globalVariables.getSettingsVariables().myPlayerNumber] =4;
        sendConnectionStateFunction();
    }

    //executed when closed i think
    @Override
    public void dispose() {
        spriteBatch.dispose();
        font.dispose();
        world.dispose();
        debugRenderer.dispose();
    }

    @Override
    public void render() {
        drawShapes();
        touches.checkTouches();
        touches.checkZoomGesture();

        if(globalVariables.getSettingsVariables().checkAllClientConnectionStates(4) && true) {
            //touch input checking

            //do physics calculations of balls
            for (Ball ball : balls) {
                ball.doPhysics();
                //Gdx.app.debug("ClassicGame", "ball player field " + globalVariables.getGameVariables().ballsPlayerScreens[ball.ballNumber]);
                /*Gdx.app.debug("ClassicGame", "ball position " + globalVariables.getGameVariables().ballsPositions[ball.ballNumber].x *PIXELS_TO_METERS
                        + " y " + globalVariables.getGameVariables().ballsPositions[ball.ballNumber].y *PIXELS_TO_METERS);*/
            }

            //do physics calculations of bats
            for (Bat bat : bats) {
                bat.doPhysics();
            }
            //step world one timestep further, ideally for 60fps, maybe needs to be adapted variably for same speeds etc
            sendFieldChangeFunction();
            sendGoalFunction();
            sendBatFunction(bats[globalVariables.getSettingsVariables().myPlayerNumber]);
            sendBallFunction();

            world.step(1 / 60f, 2, 2);

            /*Gdx.app.debug("ClassicGame", "bat position at " + bats[globalVariables.getSettingsVariables().myPlayerNumber].batBody.getPosition().x/width *PIXELS_TO_METERS
                    + " y " + bats[globalVariables.getSettingsVariables().myPlayerNumber].batBody.getPosition().y/height *PIXELS_TO_METERS);*/
            //send everything

            sendBallKineticsAL = new ArrayList<Integer>(Arrays.asList(new Integer[]{}));
            sendFieldChangeBallsAL = new ArrayList<Integer>(Arrays.asList(new Integer[]{}));
            sendFieldChangePlayersAL =new ArrayList<Integer>(Arrays.asList(new Integer[]{}));
            sendGoalAL = new ArrayList<Integer>(Arrays.asList(new Integer[]{}));

        } else {
            Gdx.app.debug("ClassicGame", " not all players ready");
            for(int i=0; i<globalVariables.getSettingsVariables().numberOfPlayers;i++){
                Gdx.app.debug("ClassicGame", " state of player "+i + " : " + globalVariables.getSettingsVariables().clientConnectionStates[i]);
            }
        }
        touches.updateLasts();
        frameNumber++;
    }

    /********* SEND FUNCTIONS *********/

    //pretty self explanatory
    void sendBallFunction() {
        if (sendBallKineticsAL.size()>0) {
            sendBallKinetics.myPlayerNumber=globalVariables.getSettingsVariables().myPlayerNumber;
            sendBallKinetics.ballNumbers = sendBallKineticsAL.toArray(new Integer[0]);
            sendBallKinetics.ballPlayerFields = new Integer[sendBallKineticsAL.size()];
            sendBallKinetics.ballPositions = new Vector2[sendBallKineticsAL.size()];
            sendBallKinetics.ballVelocities = new Vector2[sendBallKineticsAL.size()];


            for (int i = 0; i < sendBallKineticsAL.size(); i++) {
                sendBallKinetics.ballPlayerFields[i] = globalVariables.getSettingsVariables().myPlayerNumber;
                sendBallKinetics.ballPositions[i] = globalVariables.getGameVariables().downScaleVector(balls[sendBallKinetics.ballNumbers[i]].ballBody.getPosition()).scl(PIXELS_TO_METERS);
                sendBallKinetics.ballVelocities[i] = globalVariables.getGameVariables().downScaleVector(balls[sendBallKinetics.ballNumbers[i]].ballBody.getLinearVelocity()).scl(PIXELS_TO_METERS);
            }

            globalVariables.getSettingsVariables().sendToAllClients(sendBallKinetics,"udp");
            //Gdx.app.debug("ClassicGame", "ball "+Integer.toString(theBall.ballNumber)+" sent");
        }
    }

    void sendFieldChangeFunction() {
        if (sendFieldChangeBallsAL.size()>0) {
            sendBallScreenChange.myPlayerNumber=globalVariables.getSettingsVariables().myPlayerNumber;
            sendBallScreenChange.ballNumbers = sendFieldChangeBallsAL.toArray(new Integer[0]);
            sendBallScreenChange.ballPlayerFields = sendFieldChangePlayersAL.toArray(new Integer[0]);
            sendBallScreenChange.ballPositions = new Vector2[sendFieldChangeBallsAL.size()];
            sendBallScreenChange.ballVelocities = new Vector2[sendFieldChangeBallsAL.size()];


            for (int i = 0; i < sendFieldChangeBallsAL.size(); i++) {
                sendBallScreenChange.ballPositions[i] = globalVariables.getGameVariables().downScaleVector(balls[sendBallScreenChange.ballNumbers[i]].ballBody.getPosition()).scl(PIXELS_TO_METERS);
                sendBallScreenChange.ballVelocities[i] = globalVariables.getGameVariables().downScaleVector(balls[sendBallScreenChange.ballNumbers[i]].ballBody.getLinearVelocity()).scl(PIXELS_TO_METERS);
                //Gdx.app.debug("ClassicGame", "send fieldchange of ball "+sendBallScreenChange.ballNumbers[i]);
            }
            globalVariables.getSettingsVariables().sendToAllClients(sendBallScreenChange,"tcp");
        }
    }

    void sendGoalFunction() {
        if (sendGoalAL.size()>0) {
            sendBallGoal.myPlayerNumber=globalVariables.getSettingsVariables().myPlayerNumber;
            sendBallGoal.ballNumbers = sendGoalAL.toArray(new Integer[0]);
            sendBallGoal.playerScores=globalVariables.getGameVariables().playerScores;
            //Gdx.app.debug("ClassicGame", "send ballgoal");
            globalVariables.getSettingsVariables().sendToAllClients(sendBallGoal,"tcp");
        }
    }

    void sendBatFunction(Bat theBat) {
        sendBat.myPlayerNumber=globalVariables.getSettingsVariables().myPlayerNumber;
        sendBat.batPlayerField =globalVariables.getSettingsVariables().myPlayerNumber;
        sendBat.batPosition=globalVariables.getGameVariables().downScaleVector(theBat.batBody.getPosition()).scl(PIXELS_TO_METERS);
        sendBat.batOrientation=theBat.batBody.getAngle();
        globalVariables.getSettingsVariables().sendToAllClients(sendBat,"udp");
    }

    void sendConnectionStateFunction() {
        sendConnectionState.myPlayerNumber=globalVariables.getSettingsVariables().myPlayerNumber;
        sendConnectionState.connectionState=globalVariables.getSettingsVariables().clientConnectionStates[globalVariables.getSettingsVariables().myPlayerNumber];
        globalVariables.getSettingsVariables().sendToAllClients(sendConnectionState,"tcp");
    }



    /********* OTHER FUNCTIONS *********/

    //adjust camera for zooming
    private boolean zoom (float originalDistance, float currentDistance){
        float newZoomLevel=zoomLevel+(originalDistance-currentDistance)/5000;
        if(newZoomLevel<=2.0f && newZoomLevel>=1.0f) {
            zoomLevel=newZoomLevel;

        } else if(newZoomLevel>2.0f) {
            zoomLevel=newZoomLevel;//2.0f;
        } else if(newZoomLevel<1.0f) {
            zoomLevel=1.0f;
        }
        camera.zoom=zoomLevel;
        camera.position.set(0,-height+height/2*zoomLevel,0);
        camera.update();

        return false;
    }

    //transform touch input for variable zoomlevel
    Vector2 transformZoom(Vector2 vec) {
        Vector2 camPos = new Vector2(0,-height+height/2*zoomLevel);
        vec.x*=zoomLevel;
        vec.y = - height + (vec.y + height) * zoomLevel;
        return(vec);
    }

    float[] vecToFloatArray(Vector2[] vectorArray) {
        float[] floatArray = new float[vectorArray.length*2];
        for(int i=0;i<vectorArray.length;i++) {
            floatArray[2*i]=vectorArray[i].x;
            floatArray[2*i+1]=vectorArray[i].y;
        }
        return(floatArray);
    }

    Vector2[] transformVectorArray(Vector2[] vectorArray, float scale, float degrees) {
        for(Vector2 vector : vectorArray) {
            vector.scl(scale).rotate(degrees);
        }
        return(vectorArray);
    }

    void drawShapes() {
        shapeRenderer.setProjectionMatrix(camera.combined);

        gameField.display();

        for (Bat bat : bats) {
            bat.display();
        }

        for(Ball ball : balls) {
            ball.display();
        }

        touches.drawTouchPoints();

        spriteBatch.setProjectionMatrix(camera.combined);
        spriteBatch.begin();

        //show fps
        font.draw(spriteBatch,"fps: "+Float.toString(Gdx.graphics.getFramesPerSecond()), width /3, -20);
        String tempScore = "";
        for(int i=0; i<globalVariables.getSettingsVariables().numberOfPlayers;i++) {
            if(i!=0) tempScore+=":";
            tempScore += globalVariables.getGameVariables().playerScores[i];
        }
        font.draw(spriteBatch,tempScore, 0, -100);

        spriteBatch.end();

        //uncomment for box2d bodies to be shown
        //debugRenderer.render(world,camera.combined.cpy().scale(PIXELS_TO_METERS,PIXELS_TO_METERS,1));
    }

    /********* CLASSES *********/

    //create class Ball
    class Ball {
        private Body ballBody;

        private float ballRadius;

        private float[][] ballColor;
        private int ballNumber,tempPlayerField;

        //arraylist with max length for balltrace
        private BoundedArrayList<Vector2> ballPositionArrayList;
        private int ballPositionArrayListLength;
        private int ballPositionFrameSkip=4;
        private long ballUpdateCounter=0;

        private Vector2 ballForwardPosition;

        //constructor for Ball
        Ball(Vector2 position_, float radius_,int ballNumber_,int playerScreen_, int ballPositionArrayListLength_) {


            this.ballRadius =radius_;
            this.ballNumber=ballNumber_;
            this.ballPositionArrayListLength=ballPositionArrayListLength_;

            this.ballColor=new float[globalVariables.getSettingsVariables().numberOfPlayers][3];
            this.ballColor[0] = new float[]{62f / 255f,143f / 255f,215f / 255f};
            this.ballColor[1] = new float[]{186 / 255f,106f / 255f,133f / 255f};
            for(int i=2;i<globalVariables.getSettingsVariables().numberOfPlayers;i++) {
                this.ballColor[i] = new float[]{62f / 255f,143f / 255f,0f / 255f};
            }


            //physics stuff
            BodyDef ballBodyDef= new BodyDef();
            this.ballPositionArrayList = new BoundedArrayList(this.ballPositionArrayListLength);

            //set bodytype
            ballBodyDef.type = BodyDef.BodyType.DynamicBody;

            //better collisions for fast moving objects
            ballBodyDef.bullet=true;

            ballBodyDef.position.set(position_);
            this.ballBody = world.createBody(ballBodyDef);

            CircleShape ballShape = new CircleShape();
            ballShape.setPosition(new Vector2(0,0));
            ballShape.setRadius(this.ballRadius);

            FixtureDef ballFixtureDef = new FixtureDef();
            ballFixtureDef.shape= ballShape;
            //density for physics calculations
            ballFixtureDef.density=1f;
            //set collision filters
            ballFixtureDef.filter.categoryBits = CATEGORY_BALL;
            ballFixtureDef.filter.maskBits = MASK_BALL;

            this.ballBody.createFixture(ballFixtureDef);
            ballShape.dispose();

            this.ballForwardPosition = this.ballBody.getPosition().cpy().scl(PIXELS_TO_METERS);

            //Gdx.app.debug("ClassicGame checkPlayerField", "ballforwardposition x " + Float.toString(this.ballForwardPosition.x) + " y "+ Float.toString(this.ballForwardPosition.y));

            globalVariables.getGameVariables().ballsPlayerScreens[this.ballNumber]=this.checkPlayerField();
        }

        void doPhysics() {
            //if ball on my screen apply forces etc. else update position and velocity by the ones stored globally and received from other player

            if(globalVariables.getGameVariables().ballDisplayStates[this.ballNumber]) {
                if(globalVariables.getGameVariables().updateBallStates[this.ballNumber]) {
                    Vector2 tempPosition = globalVariables.getGameVariables().ballsPositions[this.ballNumber];
                    Vector2 tempVelocity = globalVariables.getGameVariables().ballsVelocities[this.ballNumber];
                    this.ballForwardPosition = tempPosition.cpy().add(tempVelocity.cpy().scl(this.ballRadius*PIXELS_TO_METERS/tempVelocity.len()));
                    if(gameField.gameFieldPolygon.contains(ballForwardPosition)) {//TODO
                        this.ballBody.setTransform(tempPosition.cpy().scl(1/PIXELS_TO_METERS), 0);
                        this.ballBody.setLinearVelocity(tempVelocity.cpy().scl(1/PIXELS_TO_METERS));
                    }
                    globalVariables.getGameVariables().updateBallStates[this.ballNumber]=false;
                }
                //Gdx.app.debug("ClassicGame", "ball "+ this. ballNumber+ " globals playerfield "+globalVariables.getGameVariables().ballsPlayerScreens[this.ballNumber]);
                if (globalVariables.getGameVariables().ballsPlayerScreens[this.ballNumber] == globalVariables.getSettingsVariables().myPlayerNumber) {
                    if(!this.checkGoal()) {
                        this.ballForwardPosition = this.ballBody.getPosition().cpy().scl(PIXELS_TO_METERS).add(this.ballBody.getLinearVelocity().cpy().scl(this.ballRadius*PIXELS_TO_METERS/this.ballBody.getLinearVelocity().len()));

                        //Gdx.app.debug("ClassicGame", "ball "+Integer.toString(this.ballNumber)+" computed");
                        this.ballBody.setType(BodyDef.BodyType.DynamicBody);
                        for (int j = 0; j < touches.maxTouchCount; j++) {
                            if (touches.isTouched[j]) {
                                if (globalVariables.getGameVariables().attractionState) {
                                    //attraction
                                    this.ballBody.applyForceToCenter(touches.touchPos[j].cpy().scl(1 / PIXELS_TO_METERS).sub(balls[this.ballNumber].ballBody.getPosition()), true);
                                }
                            }
                        }
                        if (globalVariables.getGameVariables().gravityState) {
                            //gravity
                            this.ballBody.applyForceToCenter(new Vector2(0, -2f), true);//-(this.ballBody.getPosition().y+height/PIXELS_TO_METERS)*1f
                        }

                        tempPlayerField = this.checkPlayerField();
                        //Gdx.app.debug("ClassicGame", "tempplayerfield "+tempPlayerField);
                        if(tempPlayerField==globalVariables.getSettingsVariables().myPlayerNumber) {
                            sendBallKineticsAL.add(this.ballNumber);
                        } else {
                            sendFieldChangeBallsAL.add(this.ballNumber);
                            sendFieldChangePlayersAL.add(tempPlayerField);
                        }
                    }
                } else {
                    this.ballBody.setType(BodyDef.BodyType.KinematicBody);
                    //Gdx.app.debug("ClassicGame", "ball "+Integer.toString(this.ballNumber)+" NOT computed");
                }
                if (this.ballUpdateCounter % this.ballPositionFrameSkip == 0 && globalVariables.getGameVariables().ballDisplayStates[this.ballNumber]) {
                    //add position for balltrace
                    this.ballPositionArrayList.addLast(this.ballBody.getPosition().cpy().scl(PIXELS_TO_METERS));
                }
                this.ballUpdateCounter++;
            } else {
                this.destroyBall();
            }
        }

        void display() {
            //color depending on playerfield
            if(globalVariables.getGameVariables().ballDisplayStates[this.ballNumber]) {
                shapeRenderer.begin(ShapeType.Filled);
                shapeRenderer.setColor(this.ballColor[globalVariables.getGameVariables().ballsPlayerScreens[this.ballNumber]][0],
                        this.ballColor[globalVariables.getGameVariables().ballsPlayerScreens[this.ballNumber]][1],
                        this.ballColor[globalVariables.getGameVariables().ballsPlayerScreens[this.ballNumber]][2], 1f);

                //display trace
                for (Vector2 pos : this.ballPositionArrayList) {
                    shapeRenderer.circle(pos.x, pos.y, 5);
                    //Gdx.app.debug("ClassicGame", "pos x " +Float.toString(pos.x)+" y "+ Float.toString(pos.y));
                }
                shapeRenderer.circle(this.ballBody.getPosition().x * PIXELS_TO_METERS, this.ballBody.getPosition().y * PIXELS_TO_METERS, this.ballRadius * PIXELS_TO_METERS, 30);
                shapeRenderer.end();
            }
        }

        int checkPlayerField() {
            if(!gameField.playerFieldPolygons[globalVariables.getSettingsVariables().myPlayerNumber].contains(this.ballForwardPosition)) {
                if(!gameField.playerFieldPolygons[globalVariables.getSettingsVariables().myPlayerNumber].contains(this.ballBody.getPosition().cpy().scl(PIXELS_TO_METERS))) {
                    for(int i=0;i<globalVariables.getSettingsVariables().numberOfPlayers;i++) {
                        if(i!=globalVariables.getSettingsVariables().myPlayerNumber) {
                            if(gameField.playerFieldPolygons[i].contains(this.ballForwardPosition)) {
                                if (gameField.playerFieldPolygons[i].contains(this.ballBody.getPosition().cpy().scl(PIXELS_TO_METERS))) {
                                    //Gdx.app.debug("ClassicGame", "ball on playerField " + i);
                                    return(i);

                                }
                            }
                        }
                    }
                }
            }
            return(globalVariables.getSettingsVariables().myPlayerNumber);
        }

        boolean checkGoal() {
            if (gameField.goalPolygons[globalVariables.getSettingsVariables().myPlayerNumber].contains(this.ballBody.getPosition().cpy().scl(PIXELS_TO_METERS))) {
                //Gdx.app.debug("ClassicGame", "ball " + Integer.toString(this.ballNumber) + " in my goal");
                this.destroyBall();
                globalVariables.getGameVariables().playerScores[globalVariables.getSettingsVariables().myPlayerNumber]--;
                sendGoalAL.add(this.ballNumber);
                return(true);
            }
            return(false);
        }

        void destroyBall() {
            if(this.ballBody!=null) {
                globalVariables.getGameVariables().ballDisplayStates[this.ballNumber] = false;
                world.destroyBody(this.ballBody);
                this.ballBody.setUserData(null);
                this.ballBody = null;
            }
        }

    }

    class Bat {
        private Body batBody;
        private int batPlayerField;
        private float batWidth = width/4;
        private float batHeight = height/40;
        private Vector2 newPos;
        Bat(int batPlayerField_) {
            this.batPlayerField =batPlayerField_;
            BodyDef bodyDef= new BodyDef();
            if(batPlayerField_==globalVariables.getSettingsVariables().myPlayerNumber) {
                bodyDef.type= BodyDef.BodyType.DynamicBody;
                //bodyDef.position.set(touches.touchPos[0].cpy().scl(1/PIXELS_TO_METERS));
            } else {
                bodyDef.type= BodyDef.BodyType.KinematicBody;
                //bodyDef.position.set();
            }

            this.batBody=world.createBody(bodyDef);
            PolygonShape batShape= new PolygonShape();
            batShape.setAsBox(this.batWidth/2/PIXELS_TO_METERS,this.batHeight/2/PIXELS_TO_METERS);
            FixtureDef batFixtureDef= new FixtureDef();
            //batFixtureDef.restitution=0f;
            batFixtureDef.shape = batShape;
            batShape.dispose();
            batFixtureDef.density=1f;
            batFixtureDef.friction=1f;
            batFixtureDef.restitution=0.5f;
            batFixtureDef.filter.categoryBits = CATEGORY_BAT;
            batFixtureDef.filter.maskBits = MASK_BAT;
            this.batBody.createFixture(batFixtureDef);
            this.batBody.setLinearDamping(100);
            this.batBody.setAngularDamping(1000);
            this.batBody.setTransform(touches.touchPos[0].cpy().scl(1/PIXELS_TO_METERS).rotate(360f/globalVariables.getSettingsVariables().
                    numberOfPlayers*(this.batPlayerField-globalVariables.getSettingsVariables().myPlayerNumber)),2f*MathUtils.PI/globalVariables.getSettingsVariables().numberOfPlayers*((this.batPlayerField-globalVariables.getSettingsVariables().myPlayerNumber)));

            this.newPos = touches.touchPos[0];
            globalVariables.getGameVariables().batPositions[this.batPlayerField]=globalVariables.getGameVariables().downScaleVector(this.batBody.getPosition()).scl(PIXELS_TO_METERS);
        }

        void doPhysics() {
            //similar to ball if my bat then physics else only change position etc.
            float orientation=0;
            if(this.batPlayerField ==globalVariables.getSettingsVariables().myPlayerNumber) {
                //update new position if touch inside my field
                if(gameField.playerFieldPolygons[globalVariables.getSettingsVariables().myPlayerNumber].contains(touches.touchPos[0])) {
                    if(touches.isTouched[0] && !touches.isTouched[1]) {
                        this.newPos = touches.touchPos[0];
                    }
                }
                //force to physically move to touched position
                Vector2 forceVector = this.newPos.cpy().scl(1/PIXELS_TO_METERS).sub(this.batBody.getPosition());
                //torque to set orientation
                float torque = -(this.batBody.getAngle()-orientation)*5000f;
                //forceVector.scl(1/forceVector.len());
                forceVector.scl(5000f);
                forceVector.sub(this.batBody.getLinearVelocity().cpy().scl(0));
                this.batBody.applyForceToCenter(forceVector,true);
                this.batBody.applyTorque(torque,true);
            } else {
                this.batBody.setTransform(globalVariables.getGameVariables().batPositions[this.batPlayerField].cpy().scl(1/PIXELS_TO_METERS),globalVariables.getGameVariables().batOrientations[this.batPlayerField]);
                //this.batBody.setLinearVelocity(new Vector2(globalVariables.getGameVariables().batPositions[this.batPlayerField].x*width/PIXELS_TO_METERS,globalVariables.getGameVariables().batPositions[this.batPlayerField].y*height/PIXELS_TO_METERS));

            }

        }

        void display() {
            //rotate and translate needed to properly display bat with orientation
            shapeRenderer.begin(ShapeType.Filled);
            shapeRenderer.setColor(196/255f, 106/255f, 78/255f, 1);
            shapeRenderer.identity();
            shapeRenderer.translate(this.batBody.getPosition().x*PIXELS_TO_METERS, this.batBody.getPosition().y*PIXELS_TO_METERS,0);
            shapeRenderer.rotate(0, 0,1, this.batBody.getAngle()* (180f/MathUtils.PI));
            shapeRenderer.rect(-this.batWidth/2, -this.batHeight/2, this.batWidth, this.batHeight);
            shapeRenderer.identity();
            //shapeRenderer.rotate(0, 0,1, -this.batBody.getAngle()* (180f/MathUtils.PI));
            //shapeRenderer.translate(-this.batBody.getPosition().x*PIXELS_TO_METERS, -this.batBody.getPosition().y*PIXELS_TO_METERS,0);
            shapeRenderer.end();
        }

    }

    class GameField{
        //private Body fieldLineBody;
        private Body[] borderBodies;
        private Polygon[] playerFieldPolygons;
        private Polygon[] goalPolygons;
        private Polygon gameFieldPolygon;

        private Vector2[] playerFieldVertices;
        private Vector2[] gameFieldVertices;
        private Vector2[] lineVertices;

        private PolygonSprite[] playerFieldSprites;
        private PolygonSprite[] goalSprites;
        GameField() {
            //TODO generalize to more players
            float borderThickness=width/5;
            float heightPart=0.9f;
            float heightLowPart=0.97f;

            this.playerFieldPolygons = new Polygon[globalVariables.getSettingsVariables().numberOfPlayers];
            this.goalPolygons = new Polygon[globalVariables.getSettingsVariables().numberOfPlayers];
            this.gameFieldVertices= new Vector2[7*globalVariables.getSettingsVariables().numberOfPlayers];
            this.lineVertices= new Vector2[globalVariables.getSettingsVariables().numberOfPlayers];

            //EdgeShape fieldLineShape = new EdgeShape();


            for (int i=0;i<playerFieldPolygons.length;i++) {
                this.playerFieldPolygons[i]=new Polygon();
                this.goalPolygons[i] = new Polygon();
            }

            PolygonShape[] borderShapes;

            if (globalVariables.getSettingsVariables().numberOfPlayers==2) {
                this.playerFieldVertices = new Vector2[16];
                this.gameFieldVertices= new Vector2[14];

                this.playerFieldVertices[0] = new Vector2(0,0);
                this.playerFieldVertices[1] = new Vector2(-width/2,0);
                this.playerFieldVertices[2] = new Vector2(-width/2,-height*heightPart);
                this.playerFieldVertices[3] = new Vector2(-width/4,-height*heightLowPart);
                this.playerFieldVertices[4] = new Vector2(-width/4,-height-borderThickness);
                this.playerFieldVertices[5] = new Vector2(width/4,-height-borderThickness);
                this.playerFieldVertices[6] = new Vector2(width/4,-height*heightLowPart);
                this.playerFieldVertices[7] = new Vector2(width/2,-height*heightPart);
                this.playerFieldVertices[8] = new Vector2(width/2,0);

                this.playerFieldVertices[9] = new Vector2(this.playerFieldVertices[2].x,-this.playerFieldVertices[2].y);
                this.playerFieldVertices[10] = new Vector2(this.playerFieldVertices[3].x,-this.playerFieldVertices[3].y);
                this.playerFieldVertices[11] = new Vector2(this.playerFieldVertices[4].x,-this.playerFieldVertices[4].y);
                this.playerFieldVertices[12] = new Vector2(-width/2-borderThickness,height+borderThickness);
                this.playerFieldVertices[13] = new Vector2(-width/2-borderThickness,height*heightPart);
                this.playerFieldVertices[14] = new Vector2(-width/2-borderThickness,-height*heightPart);
                this.playerFieldVertices[15] = new Vector2(-width/2-borderThickness,-height-borderThickness);

                borderShapes= new PolygonShape[3*globalVariables.getSettingsVariables().numberOfPlayers];

            } else {
                Vector2 offset = new Vector2(0,-width/2f*(MathUtils.sinDeg(90f/globalVariables.getSettingsVariables().numberOfPlayers)/MathUtils.cosDeg(90f/globalVariables.getSettingsVariables().numberOfPlayers)));
                this.playerFieldVertices = new Vector2[14];

                this.playerFieldVertices[0] = new Vector2(0,0);
                this.playerFieldVertices[1] = new Vector2(-width/2,0).add(offset);
                this.playerFieldVertices[2] = new Vector2(-width/2,-height*heightPart);
                this.playerFieldVertices[3] = new Vector2(-width/4,-height*heightLowPart);
                this.playerFieldVertices[4] = new Vector2(-width/4,-height-borderThickness);
                this.playerFieldVertices[5] = new Vector2(width/4,-height-borderThickness);
                this.playerFieldVertices[6] = new Vector2(width/4,-height*heightLowPart);
                this.playerFieldVertices[7] = new Vector2(width/2,-height*heightPart);
                this.playerFieldVertices[8] = new Vector2(width/2,0).add(offset);

                this.playerFieldVertices[10] = this.playerFieldVertices[7].cpy().rotate(-360f/globalVariables.getSettingsVariables().
                        numberOfPlayers);
                this.playerFieldVertices[11] = this.playerFieldVertices[6].cpy().rotate(-360f/globalVariables.getSettingsVariables().
                        numberOfPlayers);
                this.playerFieldVertices[12] = this.playerFieldVertices[5].cpy().rotate(-360f/globalVariables.getSettingsVariables().
                        numberOfPlayers);

                this.playerFieldVertices[9] = this.playerFieldVertices[2].cpy().add(this.playerFieldVertices[10]).sub(this.playerFieldVertices[1]);

                this.playerFieldVertices[13] = this.playerFieldVertices[9].cpy().scl((height+borderThickness)/this.playerFieldVertices[9].len());

                borderShapes = new PolygonShape[5*globalVariables.getSettingsVariables().numberOfPlayers];

            }

            for (int i=0;i<borderShapes.length;i++) {
                borderShapes[i] = new PolygonShape();
            }
            this.borderBodies = new Body[borderShapes.length];

            this.playerFieldVertices = transformVectorArray(playerFieldVertices,1/PIXELS_TO_METERS,0).clone();
            //fieldLineShape.set(playerFieldVertices[1], playerFieldVertices[8]);
            this.playerFieldVertices = transformVectorArray(playerFieldVertices,PIXELS_TO_METERS,0).clone();

            //rotate for perspective of player
            this.playerFieldVertices = transformVectorArray(playerFieldVertices,1,-360f/globalVariables.getSettingsVariables().numberOfPlayers*globalVariables.getSettingsVariables().myPlayerNumber).clone();

            for(int i = 0; i<globalVariables.getSettingsVariables().numberOfPlayers; i++) {
                this.playerFieldPolygons[i] = new Polygon(vecToFloatArray(new Vector2[]{this.playerFieldVertices[0],this.playerFieldVertices[1],this.playerFieldVertices[2],this.playerFieldVertices[3],
                        this.playerFieldVertices[6],this.playerFieldVertices[7],this.playerFieldVertices[8]}));
                this.goalPolygons[i] = new Polygon(vecToFloatArray(new Vector2[]{this.playerFieldVertices[3],this.playerFieldVertices[4],this.playerFieldVertices[5],
                        this.playerFieldVertices[6]}));
                this.lineVertices[i]= playerFieldVertices[1].cpy();
                //TODO generalize for more players
                for(int j=1;j<8;j++) {
                    this.gameFieldVertices[j-1+i*7] = new Vector2(this.playerFieldVertices[j]);
                }

                this.playerFieldVertices = transformVectorArray(this.playerFieldVertices,1/PIXELS_TO_METERS,0).clone();
                if (globalVariables.getSettingsVariables().numberOfPlayers==2) {
                    borderShapes[0 + i * 3].set(new Vector2[]{this.playerFieldVertices[9], this.playerFieldVertices[10], this.playerFieldVertices[11], this.playerFieldVertices[12],this.playerFieldVertices[13]});
                    borderShapes[1 + i * 3].set(new Vector2[]{this.playerFieldVertices[2], this.playerFieldVertices[1], this.playerFieldVertices[9], this.playerFieldVertices[13], this.playerFieldVertices[14]});
                    borderShapes[2 + i * 3].set(new Vector2[]{this.playerFieldVertices[4], this.playerFieldVertices[3], this.playerFieldVertices[2], this.playerFieldVertices[14], this.playerFieldVertices[15]});
                } else {
                    borderShapes[0 + i * 5].set(new Vector2[]{this.playerFieldVertices[2], this.playerFieldVertices[1], this.playerFieldVertices[10], this.playerFieldVertices[9]});
                    borderShapes[1 + i * 5].set(new Vector2[]{this.playerFieldVertices[10], this.playerFieldVertices[11], this.playerFieldVertices[12]});
                    borderShapes[2 + i * 5].set(new Vector2[]{this.playerFieldVertices[4], this.playerFieldVertices[3],this.playerFieldVertices[2]});
                    borderShapes[3 + i * 5].set(new Vector2[]{this.playerFieldVertices[4], this.playerFieldVertices[2],this.playerFieldVertices[9]});
                    borderShapes[4 + i * 5].set(new Vector2[]{this.playerFieldVertices[10], this.playerFieldVertices[12],this.playerFieldVertices[9]});
                }

                this.playerFieldVertices = transformVectorArray(this.playerFieldVertices,PIXELS_TO_METERS,0).clone();
                this.playerFieldVertices = transformVectorArray(this.playerFieldVertices,1,360f/globalVariables.getSettingsVariables().numberOfPlayers).clone();
                //Gdx.app.debug("ClassicGame", "bordersetup player " + i);
            }
            this.gameFieldPolygon = new Polygon(vecToFloatArray(this.gameFieldVertices));

            BodyDef borderBodyDef= new BodyDef();
            borderBodyDef.type = BodyDef.BodyType.StaticBody;
            borderBodyDef.position.set(0,0);
            FixtureDef borderFixtureDef=new FixtureDef();
            borderFixtureDef.restitution = 0.7f;
            borderFixtureDef.filter.categoryBits=CATEGORY_BORDER;
            borderFixtureDef.filter.maskBits=MASK_BORDER;

            FixtureDef fieldLineFixtureDef=new FixtureDef();
            fieldLineFixtureDef.filter.categoryBits=CATEGORY_FIELDLINE;
            fieldLineFixtureDef.filter.maskBits=MASK_FIELDLINE;

            for(int i = 0; i< this.borderBodies.length; i++) {
                this.borderBodies[i] = world.createBody(borderBodyDef);
                borderFixtureDef.shape = borderShapes[i];
                this.borderBodies[i].createFixture(borderFixtureDef);
            }

            /*this.fieldLineBody =world.createBody(borderBodyDef);
            //fieldLineFixtureDef.shape = fieldLineShape;
            this.fieldLineBody.createFixture(fieldLineFixtureDef);*/

            for (int i=0;i<borderShapes.length;i++) {
                borderShapes[i].dispose();
            }
            //fieldLineShape.dispose();

            this.playerFieldSprites = new PolygonSprite[globalVariables.getSettingsVariables().numberOfPlayers];
            this.goalSprites = new PolygonSprite[globalVariables.getSettingsVariables().numberOfPlayers];
            for(int i=0; i<globalVariables.getSettingsVariables().numberOfPlayers;i++) {
                this.playerFieldSprites[i]=createFilledPolygon(this.playerFieldPolygons[i].getVertices(),new short[] {0,1,2,0,2,3,0,3,4,0,4,5,0,5,6},89/255f, 89/255f, 89/255f, 1f);
                this.goalSprites[i]=createFilledPolygon(this.goalPolygons[i].getVertices(),new short[] {0,1,3,1,2,3},1f, 1f, 1f, 1f);
            }
        }

        void display() {
            //background
            Gdx.gl.glClearColor(0, 0, 0, 1);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

            //field background
            polygonSpriteBatch.setProjectionMatrix(camera.combined);
            polygonSpriteBatch.begin();
            for(int i=0; i<globalVariables.getSettingsVariables().numberOfPlayers;i++) {
                this.playerFieldSprites[i].draw(polygonSpriteBatch);
                this.goalSprites[i].draw(polygonSpriteBatch);
            }
            polygonSpriteBatch.end();

            shapeRenderer.begin(ShapeType.Filled);
            shapeRenderer.setColor(128/255f,143/255f,133/255f,1);
            for(int i=0; i<globalVariables.getSettingsVariables().numberOfPlayers;i++) {
                shapeRenderer.rectLine(this.lineVertices[i],new Vector2(0,0),height/100);
            }
            shapeRenderer.end();




        }

        PolygonSprite createFilledPolygon(float [] verticesXY, short [] triangleIndices, float r, float g, float b, float a) {
            PolygonSprite poly;

            // Creating the color filling (but textures would work the same way)
            Pixmap pix = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
            pix.setColor(r,g,b,a); // DE is red, AD is green and BE is blue.
            pix.fill();
            Texture textureSolid = new Texture(pix);
            PolygonRegion polyRegion = new PolygonRegion(new TextureRegion(textureSolid), verticesXY,triangleIndices);
            poly = new PolygonSprite(polyRegion);
            return(poly);
        }
    }

    class Touches {
        private int maxTouchCount;
        private Vector2[] touchPos;
        private Vector2[] lastTouchPos;
        private Vector2[] startTouchPos;
        private boolean[] isTouched;
        private boolean[] lastIsTouched;

        Touches(int maxTouchCount_) {
            this.maxTouchCount=maxTouchCount_;
            this.touchPos=new Vector2[maxTouchCount];
            this.lastTouchPos=new Vector2[maxTouchCount];
            this.startTouchPos=new Vector2[maxTouchCount];
            this.isTouched=new boolean[maxTouchCount];
            this.lastIsTouched=new boolean[maxTouchCount];

            for (int i=0;i<maxTouchCount;i++) {
                this.touchPos[i]=new Vector2(0,-height*0.9f);
                this.lastTouchPos[i]=touchPos[i];
                this.startTouchPos[i]=touchPos[i];
                this.isTouched[i]=false;
                this.lastIsTouched[i]=false;
            }
        }

        //check for touches
        void checkTouches() {
            for(int i=0;i<this.maxTouchCount;i++) {
                if (Gdx.input.isTouched(i)) {
                    this.isTouched[i] = true;
                    this.touchPos[i]=transformZoom(new Vector2(Gdx.input.getX(i)-width/2,-Gdx.input.getY(i)));
                } else {
                    this.isTouched[i] = false;
                }
                if(!this.lastIsTouched[i] && this.isTouched[i]) {
                    this.startTouchPos[i]=this.touchPos[i];
                }
            }
        }

        //check for zoom gesture
        void checkZoomGesture() {
            if(this.isTouched[0] && this.isTouched[1]) {
                zoom(this.startTouchPos[0].cpy().sub(this.startTouchPos[1]).len(),this.touchPos[0].cpy().sub(this.touchPos[1]).len());
            }

            if(!this.isTouched[0] || !this.isTouched[1]) {
                if(zoomLevel != 2.0 || zoomLevel != 1.0) {
                    //continuously update camera
                    zoomLevel=MathUtils.round(zoomLevel);
                    camera.zoom=zoomLevel;
                    camera.position.set(0,-height+height/2*zoomLevel,0);
                    camera.update();
                }

            }
        }

        void updateLasts() {
            for(int i=0;i<this.maxTouchCount;i++) {
                this.lastTouchPos[i]=this.touchPos[i];
                this.lastIsTouched[i]=this.isTouched[i];
            }
        }

        //show where screen is touched
        void drawTouchPoints() {
            shapeRenderer.begin(ShapeType.Filled);
            for(int i = 0; i < this.maxTouchCount; i++) {
                if (this.isTouched[i]) {
                    shapeRenderer.setColor(0, 1, 0, 0.5f);
                    shapeRenderer.circle(this.touchPos[i].x,this.touchPos[i].y, 30, 100);
                    if(i>0) {
                        shapeRenderer.setColor(0, 1, 0, 1);
                        shapeRenderer.line(this.touchPos[i-1].x,this.touchPos[i-1].y,this.touchPos[i].x,this.touchPos[i].y);
                    }
                }
            }
            shapeRenderer.end();
        }
    }

    //custom arraylist with maximum elements, first one is kicked out if max is reached
    class BoundedArrayList<T> extends ArrayList<T> {
        private int maxSize;
        public BoundedArrayList(int size)
        {
            this.maxSize = size;
        }

        public void addLast(T e)
        {
            this.add(e);
            if(this.size() > this.maxSize)
                this.remove(0);
        }
    }

}
