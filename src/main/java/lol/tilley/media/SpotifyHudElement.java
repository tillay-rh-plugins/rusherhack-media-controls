package lol.tilley.media;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.apache.commons.io.IOUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.rusherhack.client.api.bind.key.GLFWKey;
import org.rusherhack.client.api.events.client.input.EventMouse;
import org.rusherhack.client.api.feature.hud.ResizeableHudElement;
import org.rusherhack.client.api.render.IRenderer2D;
import org.rusherhack.client.api.render.RenderContext;
import org.rusherhack.client.api.render.font.IFontRenderer;
import org.rusherhack.client.api.render.graphic.TextureGraphic;
import org.rusherhack.client.api.render.graphic.VectorGraphic;
import org.rusherhack.client.api.setting.BindSetting;
import org.rusherhack.client.api.setting.ColorSetting;
import org.rusherhack.client.api.ui.ScaledElementBase;
import org.rusherhack.client.api.utils.ChatUtils;
import org.rusherhack.client.api.utils.InputUtils;
import org.rusherhack.core.bind.key.NullKey;
import org.rusherhack.core.event.stage.Stage;
import org.rusherhack.core.event.subscribe.Subscribe;
import org.rusherhack.core.interfaces.IClickable;
import org.rusherhack.core.setting.BooleanSetting;
import org.rusherhack.core.utils.ColorUtils;
import org.rusherhack.core.utils.MathUtils;
import org.rusherhack.core.utils.Timer;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * @author John200410
 */
public class SpotifyHudElement extends ResizeableHudElement {

    public static final int BACKGROUND_COLOR = ColorUtils.transparency(Color.BLACK.getRGB(), 0.5f);

    /**
     * Settings
     */

    private final BooleanSetting background = new BooleanSetting("Background", true);
    private final ColorSetting backgroundColor = new ColorSetting("Color", new Color(BACKGROUND_COLOR, true));

    private final BooleanSetting binds = new BooleanSetting("Binds", false);
    private final BindSetting playPauseBind = new BindSetting("Play/Pause", NullKey.INSTANCE);
    private final BindSetting backBind = new BindSetting("Back", NullKey.INSTANCE);
    private final BindSetting nextBind = new BindSetting("Next", NullKey.INSTANCE);
    private final BindSetting back5Bind = new BindSetting("Back 5", NullKey.INSTANCE);
    private final BindSetting next5Bind = new BindSetting("Next 5", NullKey.INSTANCE);

    /**
     * Media Controller
     */
    private final SongInfoHandler songInfo;
    private final DurationHandler duration;
    private final MediaControllerHandler mediaController;

    /**
     * Variables
     */
    private final VectorGraphic spotifyLogo;
    private final DynamicTexture trackThumbnailTexture;
    private final PluginMain plugin;
    private boolean consumedButtonClick = false;
    private final Timer secondTimer = new Timer();

    // track variables
    private String song = "unknown";
    private String artist = "unknown";
    private String album = "unknown";
    private String thumbnail = "";
    private String url = "unknown";
    private String playStatus = "";
    private String repeatStatus = "";
    private String shuffleStatus = "";
    private boolean isPlaying = false;
    private Double trackLength = 0.0;
    private Double trackPos = 0.0;

    public SpotifyHudElement(PluginMain plugin) throws IOException {
        super("Spotify");
        this.plugin = plugin;

        this.mediaController = new MediaControllerHandler();
        this.duration = new DurationHandler();
        this.songInfo = new SongInfoHandler();

        this.spotifyLogo = new VectorGraphic("icons/spotify_logo.svg", 32, 32);
        this.trackThumbnailTexture = new DynamicTexture(640, 640, false);
        this.trackThumbnailTexture.setFilter(true, true);


        this.background.addSubSettings(backgroundColor);
        this.binds.addSubSettings(playPauseBind, backBind, nextBind, back5Bind, next5Bind);

        this.registerSettings(background, binds);

        new Thread(this::startMetadataListener, "MediaMetadataListener").start();
        new Thread(this::startPlayStatusListener, "MediaStatusListener").start();
        new Thread(this::startShuffleListener, "ShuffleListener").start();
        new Thread(this::startRepeatListener, "RepeatListener").start();

        //dont ask
        //this.setupDummyModuleBecauseImFuckingStupidAndForgotToRegisterHudElementsIntoTheEventBus();
    }

    public String playerctl(String command) {
        String[] parts = command.split(" ");
        ProcessBuilder pb = new ProcessBuilder();
        pb.command(new java.util.ArrayList<>() {{
            add("playerctl");
            this.addAll(Arrays.asList(parts));
        }});
        pb.redirectErrorStream(true);

        try {
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                return reader.readLine();
            }
        } catch (IOException e) {
            return e.getMessage();
        }
    }

    public void startMetadataListener() {
        ProcessBuilder pb = new ProcessBuilder("playerctl", "-F", "metadata");
        pb.redirectErrorStream(true);

        try {
            Process process = pb.start();
            ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
            final ScheduledFuture<?>[] pendingUpdate = {null};

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {

                    isPlaying = line.contains("    ");

                    if (line.contains("xesam:title")) {
                        song = extractValue(line);
                    }
                    if (line.contains("xesam:artist")) {
                        artist = extractValue(line);
                    }
                    if (line.contains("xesam:album ")) {
                        album = extractValue(line);
                        if (Objects.equals(album, "DJ")) {
                            song = "AI DJ";
                            artist = "DJ";
                            thumbnail = "https://lexicon-assets.spotifycdn.com/DJ-Beta-CoverArt-300.jpg";
                        }
                    }
                    if (line.contains("mpris:artUrl")) {
                        thumbnail = extractValue(line);
                    }
                    if (line.contains("mpris:length")) {
                        trackLength = Double.parseDouble(extractValue(line)) / 1000;
                    }
                    if (line.contains("xesam:url")) {
                        url = extractValue(line);
                        if (url.contains("file://") && Objects.equals(song, "unknown")) {
                            song = url.substring(7).replace("%20", " ");
                        }
                    }

                    if (pendingUpdate[0] != null) {
                        pendingUpdate[0].cancel(false);
                    }
                    pendingUpdate[0] = scheduler.schedule(() -> {
                        this.songInfo.updateSong(song, artist, album);

                        if (thumbnail.startsWith("file://")) {
                            try {
                                InputStream inputStream = new FileInputStream(thumbnail.substring(7));
                                setImageFromInputStream(inputStream);
                            } catch (Throwable e) {
                                this.plugin.getLogger().error("Failed to update local art", e);
                                try {
                                    setImageFromInputStream(new TextureGraphic("icons/noart.jpg", 640, 640).getInputStream());
                                } catch (IOException ex) {
                                    this.plugin.getLogger().error("Fallback for local failed too", ex);
                                }
                            }
                        } else if (thumbnail.startsWith("https://")) {
                            try {
                                HttpClient client = HttpClient.newHttpClient();
                                HttpRequest request = HttpRequest.newBuilder(URI.create(thumbnail)).build();
                                InputStream inputStream = client.send(request, HttpResponse.BodyHandlers.ofInputStream()).body();
                                setImageFromInputStream(inputStream);
                            } catch (Throwable e) {
                                this.plugin.getLogger().error("Failed to update remote art", e);
                                try {
                                    setImageFromInputStream(new TextureGraphic("icons/noart.jpg", 640, 640).getInputStream());
                                } catch (IOException ex) {
                                    this.plugin.getLogger().error("Fallback for remote failed too", ex);
                                }
                            }
                        } else {
                            try {
                                setImageFromInputStream(new TextureGraphic("icons/noart.jpg", 640, 640).getInputStream());
                            } catch (Throwable e) {
                                this.trackThumbnailTexture.setPixels(null);
                                this.trackThumbnailTexture.upload();
                                this.plugin.getLogger().error("Failed to update no art", e);
                            }
                        }
                        song = "unknown";
                        artist = "unknown";
                        album = "unknown";
                        thumbnail = "";
                        url = "unknown";
                        trackPos = 0.0;
                    }, 10, TimeUnit.MILLISECONDS);
                }
            }
            scheduler.shutdown();
        } catch (IOException ignored) {
        }
    }

    public void setImageFromInputStream(InputStream inputStream) throws IOException {
        BufferedImage img = ImageIO.read(inputStream);
        int w = img.getWidth(), h = img.getHeight();
        double s = Math.min(640d / w, 640d / h);
        int nw = (int)(w * s), nh = (int)(h * s);

        BufferedImage resized = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_ARGB);
        resized.getGraphics().drawImage(img, 0, 0, nw, nh, null);

        ByteArrayOutputStream imageBytes = new ByteArrayOutputStream();
        ImageIO.write(resized, "png", imageBytes);

        byte[] byteArray = imageBytes.toByteArray();
        NativeImage nativeImage = readNativeImage(byteArray);

        RenderSystem.recordRenderCall(() -> {
            this.trackThumbnailTexture.setPixels(nativeImage);
            this.trackThumbnailTexture.upload();
            this.trackThumbnailTexture.setFilter(true, true);
        });
        inputStream.close();
    }

    public void startPlayStatusListener() {
        ProcessBuilder pb = new ProcessBuilder("playerctl", "-F", "status");
        pb.redirectErrorStream(true);

        try {
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    playStatus = line;
                }
            }
        } catch (IOException ignored) {
        }
    }

    public void startShuffleListener() {
        ProcessBuilder pb = new ProcessBuilder("playerctl", "-F", "shuffle");
        pb.redirectErrorStream(true);

        try {
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    shuffleStatus = line.isEmpty() ? "Off" : line;
                }
            }
        } catch (IOException ignored) {
        }
    }

    public void startRepeatListener() {
        ProcessBuilder pb = new ProcessBuilder("playerctl", "-F", "loop");
        pb.redirectErrorStream(true);

        try {
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    repeatStatus = line;
                }
            }
        } catch (IOException ignored) {
        }
    }

    @Override
    public void tick() {
        if (secondTimer.passed(1000)) {
            secondTimer.reset();
            try {
                trackPos = isPlaying ? Double.parseDouble(playerctl("position")) : 0.0;
            } catch (Exception uhOh) {
                this.getLogger().error("very not good playerctl error", uhOh);
            }
        }
    }
    private String extractValue(String line) {
        int idx = line.indexOf(":");
        if (idx == -1) return "";
        int valueStart = line.indexOf(" ", idx);
        return valueStart != -1 ? line.substring(valueStart).trim() : "";
    }

    @Override
    public void renderContent(RenderContext context, double mouseX, double mouseY) {
        final IRenderer2D renderer = this.getRenderer();
        final IFontRenderer fr = this.getFontRenderer();
        final PoseStack matrixStack = context.pose();

        //background
        if(this.background.getValue()) {
            renderer.drawRoundedRectangle(0, 0, this.getWidth(), this.getHeight(), 5, true, false, 0, this.getFillColor(), 0);
        }
        //logo
        renderer.drawGraphicRectangle(this.spotifyLogo, this.getWidth() - 5 - 16, 5, 16, 16);



        if(!isPlaying) {
            this.trackThumbnailTexture.setPixels(null);
            fr.drawString("No status", 5, 10, -1);
            return;
        }

//        if(!url.contains("spotify")) {
//            this.trackThumbnailTexture.setPixels(null);
//            fr.drawString("Unknown media playing", 5, 10, -1);
//            return;
//        }

//		final PlaybackState.Item song = status.item;

//		if(song == null) {
//			this.trackThumbnailTexture.setPixels(null);
//			fr.drawString("No song loaded", 5, 10, -1);
//			return;
//		}

        //thumbnail
        if(this.trackThumbnailTexture.getPixels() != null) {
            renderer.drawTextureRectangle(this.trackThumbnailTexture.getId(), 65, 65, 5, 5, 65, 65, 3);
        }

        final double leftOffset = 75;

        //set correct mouse pos because its set to -1, -1 when not in hud editor
        if(!mc.mouseHandler.isMouseGrabbed()) {
            mouseX = (int) InputUtils.getMouseX();
            mouseY = (int) InputUtils.getMouseY();
        }

        /////////////////////////////////////////////////////////////////////
        //top
        /////////////////////////////////////////////////////////////////////
        double topOffset = 5;

        //song details
        this.songInfo.setX(leftOffset);
        this.songInfo.setY(topOffset);
        this.songInfo.render(renderer, context, mouseX, mouseY);
        topOffset += this.songInfo.getHeight();

        /////////////////////////////////////////////////////////////////////

        /////////////////////////////////////////////////////////////////////
        //bottom
        /////////////////////////////////////////////////////////////////////
        final double bottomOffset = this.getHeight() - 5 - this.duration.getHeight();
        this.duration.setX(leftOffset);
        this.duration.setY(bottomOffset);
        this.duration.render(renderer, context, mouseX, mouseY);

        //media controls
        this.mediaController.setX(leftOffset);
        this.mediaController.setY(topOffset);
        this.mediaController.setHeight(bottomOffset - topOffset);
        this.mediaController.render(renderer, context, mouseX, mouseY);
        /////////////////////////////////////////////////////////////////////
    }

    // clicking on the buttons while in chat
    @Subscribe(stage = Stage.PRE)
    private void onMouseClick(EventMouse.Key event) {
        if(event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return;
        }
        if(!(mc.screen instanceof ChatScreen)) {
            return;
        }

        final double mouseX = event.getMouseX();
        final double mouseY = event.getMouseY();

        final double x = this.getStartX();
        final double y = this.getStartY();

        if(mouseX >= x && mouseX <= x + this.getScaledWidth() && mouseY >= y && mouseY <= y + this.getScaledHeight() && event.getAction() == GLFW.GLFW_PRESS) {
            this.consumedButtonClick = true;
            mouseClicked(mouseX, mouseY, event.getButton());
            this.consumedButtonClick = false;
        } else if(event.getAction() == GLFW.GLFW_RELEASE) {
            mouseReleased(mouseX, mouseY, event.getButton());
        }
    }

    //keybinds
    @Subscribe(stage = Stage.PRE)
    private void onKey(EventMouse.Key event) {
        if(!this.binds.getValue()) {
            return;
        }
        if(event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }

        if(this.playPauseBind.getValue() instanceof GLFWKey key && key.getKeyCode() == event.getButton()) {
            playerctl("play-pause");
        } else if(this.backBind.getValue() instanceof GLFWKey key && key.getKeyCode() == event.getButton()) {
            playerctl("previous");
        } else if(this.nextBind.getValue() instanceof GLFWKey key && key.getKeyCode() == event.getButton()) {
           playerctl("next");
        } else if(this.back5Bind.getValue() instanceof GLFWKey key && key.getKeyCode() == event.getButton()) {
           playerctl("position 5-");
        } else if(this.next5Bind.getValue() instanceof GLFWKey key && key.getKeyCode() == event.getButton()) {
            playerctl("position 5+");
        }
    }



@Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {

        if(this.mediaController.mouseClicked(mouseX, mouseY, button)) {
            return true;
        } else if(this.duration.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        if(this.consumedButtonClick) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void mouseReleased(double mouseX, double mouseY, int button) {
        this.duration.mouseReleased(mouseX, mouseY, button);
        this.mediaController.mouseReleased(mouseX, mouseY, button);
        super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public double getWidth() {
        return 225;
    }

    @Override
    public double getHeight() {
        return 75;
    }

    @Override
    public boolean shouldDrawBackground() {
        return false;
    }

    private int getFillColor() {
        //TODO: return color based on song thumbnail
        return this.backgroundColor.getValueRGB();
    }

    private NativeImage readNativeImage(byte[] bytes) throws IOException {
        MemoryStack memoryStack = MemoryStack.stackGet();
        int i = memoryStack.getPointer();
        if (i < bytes.length) {
            ByteBuffer byteBuffer = MemoryUtil.memAlloc(bytes.length);

            NativeImage nativeImage;
            try {
                byteBuffer.put(bytes);
                byteBuffer.rewind();
                nativeImage = NativeImage.read(byteBuffer);
            } finally {
                MemoryUtil.memFree(byteBuffer);
            }

            return nativeImage;
        } else {
            try(MemoryStack memoryStack2 = MemoryStack.stackPush()) {
                ByteBuffer byteBuffer2 = memoryStack2.malloc(bytes.length);
                byteBuffer2.put(bytes);
                byteBuffer2.rewind();
                return NativeImage.read(byteBuffer2);
            }
        }
    }

    abstract class ElementHandler extends ScaledElementBase implements IClickable {

        abstract void render(IRenderer2D renderer, RenderContext context, double mouseX, double mouseY);

        @Override
        public double getWidth() {
            return SpotifyHudElement.this.getWidth() - 75 - 5;
        }

        @Override
        public double getScale() {
            return SpotifyHudElement.this.getScale();
        }

        public double getScaledX() {
            return this.getX() * this.getScale();
        }

        public double getScaledY() {
            return this.getY() * this.getScale();
        }

        @Override
        public boolean isHovered(double mouseX, double mouseY) {
            mouseX -= SpotifyHudElement.this.getStartX();
            mouseY -= SpotifyHudElement.this.getStartY();

            return mouseX >= this.getScaledX() && mouseX <= this.getScaledX() + this.getScaledWidth() && mouseY >= this.getScaledY() && mouseY <= this.getScaledY() + this.getScaledHeight();
        }

    }

    class SongInfoHandler extends ElementHandler {

        private final ScrollingText title = new ScrollingText();
        private final ScrollingText artists = new ScrollingText();
        private final ScrollingText album = new ScrollingText();

        @Override
        void render(IRenderer2D renderer, RenderContext context, double mouseX, double mouseY) {
            final IFontRenderer fr = SpotifyHudElement.this.getFontRenderer();
            final PoseStack matrixStack = context.pose();

            matrixStack.pushPose();
            matrixStack.translate(this.getX(), this.getY(), 0);
            renderer.scissorBox(0, 0, this.getWidth(), this.getHeight());

            //smaller scissorbox for title to make room for spotify logo
            final double titleMaxWidth = this.getWidth() - 20;
            renderer.scissorBox(0, -1, titleMaxWidth, this.getHeight());
            this.title.render(context, renderer, fr, titleMaxWidth, -1);

            matrixStack.translate(0, fr.getFontHeight() + 1, 0);
            matrixStack.scale(0.75f, 0.75f, 1);

            this.artists.render(context, renderer, fr, titleMaxWidth / 0.75, Color.LIGHT_GRAY.getRGB());

            renderer.popScissorBox();

            matrixStack.translate(0, fr.getFontHeight() + 1, 0);

            this.album.render(context, renderer, fr, this.getWidth() / 0.75, Color.LIGHT_GRAY.getRGB());

            renderer.popScissorBox();
            matrixStack.popPose();
        }

        public void updateSong(String song, String artist, String album) {
            this.title.setText(song);
            this.artists.setText("by " + artist);
            this.album.setText("on " + album);
        }

        @Override
        public double getHeight() {
            return (getFontRenderer().getFontHeight() + 1) * 3;
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            return false;
        }

        static class ScrollingText {

            private String text;
            private double scroll = 0;
            private boolean scrolling = false;
            private boolean scrollingForward = false;
            private final Timer pauseTimer = new Timer();
            private long lastUpdate = 0;

            void render(RenderContext context, IRenderer2D renderer, IFontRenderer fr, double width, int color) {
                if(this.text == null) {
                    return;
                }

                final double textWidth = fr.getStringWidth(this.text);
                final double maxScroll = textWidth - width;

                if(maxScroll <= 0) {
                    fr.drawString(this.text, 0, 0, color);
                    return;
                }

                if(this.scrolling) {
                    this.pauseTimer.reset();

                    if(this.scrollingForward) {
                        this.scroll += (System.currentTimeMillis() - this.lastUpdate) / 75f;

                        if(this.scroll >= maxScroll) {
                            this.scroll = maxScroll;
                            this.scrolling = false;
                        }
                    } else {
                        this.scroll -= (System.currentTimeMillis() - this.lastUpdate) / 75f;

                        if(this.scroll <= 0) {
                            this.scroll = 0;
                            this.scrolling = false;
                        }
                    }
                } else {
                    if(this.pauseTimer.passed(2500)) {
                        this.scrolling = true;
                        this.scrollingForward = !this.scrollingForward;
                    }
                }

                fr.drawString(this.text, -this.scroll, 0, color);
                this.lastUpdate = System.currentTimeMillis();
            }

            void setText(String text) {
                this.text = text;
                this.scroll = 0;
                this.pauseTimer.reset();
                this.scrolling = false;
                this.scrollingForward = false;
            }
        }
    }

    class DurationHandler extends ElementHandler {

        private static final double PROGRESS_BAR_HEIGHT = 2;

        /**
         * Variables
         */
        private boolean seeking = false;

        @Override
        void render(IRenderer2D renderer, RenderContext context, double mouseX, double mouseY) {
            final IFontRenderer fr = SpotifyHudElement.this.getFontRenderer();
            final PoseStack matrixStack = context.pose();

            final boolean hovered = this.isHovered(mouseX, mouseY);

            matrixStack.pushPose();
            matrixStack.translate(this.getX(), this.getY(), 0);

            final double mouseXOffset = SpotifyHudElement.this.getStartX() + this.getScaledX();
            final double mouseYOffset = SpotifyHudElement.this.getStartY() + this.getScaledY();
            mouseX -= (int) mouseXOffset;
            mouseY -= (int) mouseYOffset;
            mouseX = (int) (mouseX / this.getScale());
            mouseY = (int) (mouseY / this.getScale());

            final double width = this.getWidth();
            double bottomOffset = this.getHeight();
            final double seekingProgress = MathUtils.clamp((double) mouseX / width, 0, 1);

            //progress bar
            final double progressBarHeight = PROGRESS_BAR_HEIGHT;
            if (!Objects.equals(playStatus, "Playing")) {
                secondTimer.reset();
            }
            final long progress_ms = (long) (trackPos * 1000) + secondTimer.getTime();
            final double progress = (double) progress_ms / (double) trackLength;

            final boolean hoveredOverProgressBar = hovered && mouseY >= bottomOffset - progressBarHeight - 1;
            renderer.drawRoundedRectangle(0, bottomOffset - progressBarHeight, width, progressBarHeight, 1, true, false, 0, Color.GRAY.getRGB(), 0);
            renderer.drawRoundedRectangle(0, bottomOffset - progressBarHeight, width * (this.seeking ? seekingProgress : progress), progressBarHeight, 1, true, false, 0, hoveredOverProgressBar || this.seeking ? Color.GREEN.getRGB() : Color.WHITE.getRGB(), 0);
            bottomOffset -= progressBarHeight + 1;

            //duration
            final String current = String.format("%d:%02d", progress_ms / 60000, progress_ms / 1000 % 60);
            final String length = String.format("%d:%02d", (long)(trackLength / 60000), (long)(trackLength / 1000) % 60);
            final double durationHeight = fr.getFontHeight() + 1;
            fr.drawString(current, 0, bottomOffset - durationHeight, Color.LIGHT_GRAY.getRGB());
            fr.drawString(length, width - fr.getStringWidth(length), bottomOffset - durationHeight, Color.LIGHT_GRAY.getRGB());
            bottomOffset -= durationHeight - 1;

            //seeking
            if(this.seeking) {
                final int seekingProgressMs = (int) (seekingProgress * trackLength);
                final String seekingTime = String.format("%d:%02d", seekingProgressMs / 60000, seekingProgressMs / 1000 % 60);
                final double seekingTimeWidth = fr.getStringWidth(seekingTime);
                final double seekX = MathUtils.clamp(mouseX, 0, width);

                renderer.drawRoundedRectangle(seekX - seekingTimeWidth / 2f, this.getHeight() - progressBarHeight - 1 - durationHeight, seekingTimeWidth, durationHeight, 1, true, false, 0, BACKGROUND_COLOR, 0);
                fr.drawString(seekingTime, seekX - seekingTimeWidth / 2f, this.getHeight() - progressBarHeight - 1 - durationHeight, -1);

                renderer.drawCircle(seekX, this.getHeight() - 1, 3, Color.WHITE.getRGB());
            }

            matrixStack.popPose();
        }

        @Override
        public double getHeight() {
            return PROGRESS_BAR_HEIGHT + 1 + SpotifyHudElement.this.getFontRenderer().getFontHeight() + 1;
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if(!this.isHovered(mouseX, mouseY)) {
                return false;
            }

            //localize mouse pos
            mouseY -= SpotifyHudElement.this.getStartY();
            mouseY -= this.getScaledY();
            mouseY = (int) (mouseY / this.getScale());

            final boolean hoveredOverProgressBar = mouseY >= this.getHeight() - PROGRESS_BAR_HEIGHT - 1;

            if(hoveredOverProgressBar) {
                this.seeking = true;
                return true;
            }

			/*
			try {
				api.authorizationRefreshToken();
				return true;
			} catch(Exception e) {
				e.printStackTrace();
			}
			 */

            return false;
        }

        @Override
        public void mouseReleased(double mouseX, double mouseY, int button) {
            if(this.seeking) {
                this.seeking = false;

                if(song == null) {
                    return;
                }

                //localize mouse pos
                mouseX -= SpotifyHudElement.this.getStartX();
                mouseY -= SpotifyHudElement.this.getStartY();
                mouseX -= this.getScaledX();
                mouseY -= this.getScaledY();
                mouseX = (int) (mouseX / this.getScale());
                mouseY = (int) (mouseY / this.getScale());


                final double progress = MathUtils.clamp(mouseX / this.getWidth(), 0, 1);
                final int position = (int) (progress * trackLength / 1000);
                playerctl("position " + position);
            }

            super.mouseReleased(mouseX, mouseY, button);
        }

    }

    class MediaControllerHandler extends ElementHandler {

        private static final double CONTROL_SIZE = 16;
        private static final double PAUSE_PLAY_SIZE = CONTROL_SIZE + 4; //bigger than other controls

        /**
         * Controls
         */
        private final VectorGraphic playGraphic, pauseGraphic;
        private final VectorGraphic backGraphic, nextGraphic;
        private final VectorGraphic shuffleOnGraphic, shuffleOffGraphic;
        private final VectorGraphic loopOffGraphic, loopAllGraphic, loopSameGraphic;
        private double playPauseX, backX, nextX, shuffleX, loopX;

        /**
         * Variables
         */
        private double height;

        public MediaControllerHandler() throws IOException {
            //load graphics
            this.playGraphic = new VectorGraphic("icons/play.svg", 48, 48);
            this.pauseGraphic = new VectorGraphic("icons/pause.svg", 48, 48);
            this.backGraphic = new VectorGraphic("icons/back.svg", 48, 48);
            this.nextGraphic = new VectorGraphic("icons/next.svg", 48, 48);
            this.shuffleOnGraphic = new VectorGraphic("icons/shuffle_on.svg", 48, 48);
            this.shuffleOffGraphic = new VectorGraphic("icons/shuffle_off.svg", 48, 48);
            this.loopOffGraphic = new VectorGraphic("icons/loop_off.svg", 48, 48);
            this.loopAllGraphic = new VectorGraphic("icons/loop_all.svg", 48, 48);
            this.loopSameGraphic = new VectorGraphic("icons/loop_same.svg", 48, 48);
        }

        //TODO: this could use some object oriented programming
        public void render(IRenderer2D renderer, RenderContext context, double mouseX, double mouseY) {
            final PoseStack matrixStack = context.pose();
            matrixStack.pushPose();
            matrixStack.translate(this.getX(), this.getY(), 0);

            final boolean hovered = this.isHovered(mouseX, mouseY);
            final double mouseXOffset = SpotifyHudElement.this.getStartX() + this.getScaledX();
            final double mouseYOffset = SpotifyHudElement.this.getStartY() + this.getScaledY();
            mouseX -= (int) mouseXOffset;
            mouseY -= (int) mouseYOffset;
            mouseX = (int) (mouseX / this.getScale());
            mouseY = (int) (mouseY / this.getScale());

            final double width = this.getWidth();
            final double mediaControlsCenter = this.getHeight() / 2f;

            //play/pause
            final VectorGraphic playPauseGraphic = Objects.equals(playStatus, "Playing") ? this.pauseGraphic : this.playGraphic;
            this.playPauseX = width / 2f - PAUSE_PLAY_SIZE / 2f;
            final boolean playPauseHovered = hovered && mouseX >= this.playPauseX && mouseX <= this.playPauseX + PAUSE_PLAY_SIZE && mouseY <= this.getScaledY() + PAUSE_PLAY_SIZE;
            if(playPauseHovered) {
                renderer.drawRoundedRectangle(this.playPauseX - 1, mediaControlsCenter - PAUSE_PLAY_SIZE / 2f - 1, PAUSE_PLAY_SIZE + 2, PAUSE_PLAY_SIZE + 2, 3, true, false, 0, BACKGROUND_COLOR, 0);
            }
            renderer.drawGraphicRectangle(playPauseGraphic, this.playPauseX, mediaControlsCenter - PAUSE_PLAY_SIZE / 2f, PAUSE_PLAY_SIZE, PAUSE_PLAY_SIZE);

            final double smallerGraphicY = mediaControlsCenter - CONTROL_SIZE / 2f;
            double mediaLeftOffset = width / 2f - CONTROL_SIZE / 2f - 5;
            double mediaRightOffset = width / 2f + CONTROL_SIZE / 2f + 5;

            //back
            this.backX = mediaLeftOffset - CONTROL_SIZE;
            final boolean backHovered = hovered && mouseX >= this.backX && mouseX <= this.backX + CONTROL_SIZE && mouseY <= this.getScaledY() + CONTROL_SIZE;
            if(backHovered) {
                renderer.drawRoundedRectangle(this.backX, smallerGraphicY, CONTROL_SIZE, CONTROL_SIZE, 3, true, false, 0, BACKGROUND_COLOR, 0);
            }
            renderer.drawGraphicRectangle(this.backGraphic, this.backX, smallerGraphicY, CONTROL_SIZE, CONTROL_SIZE);
            mediaLeftOffset -= CONTROL_SIZE + 5;

            //next
            this.nextX = mediaRightOffset;
            final boolean nextHovered = hovered && mouseX >= this.nextX && mouseX <= this.nextX + CONTROL_SIZE && mouseY <= this.getScaledY() + CONTROL_SIZE;
            if(nextHovered) {
                renderer.drawRoundedRectangle(this.nextX, smallerGraphicY, CONTROL_SIZE, CONTROL_SIZE, 3, true, false, 0, BACKGROUND_COLOR, 0);
            }
            renderer.drawGraphicRectangle(this.nextGraphic, this.nextX, smallerGraphicY, CONTROL_SIZE, CONTROL_SIZE);
            mediaRightOffset += CONTROL_SIZE + 5;

            //shuffle
            final VectorGraphic shuffleGraphic = shuffleStatus.equals("On") ? this.shuffleOnGraphic : this.shuffleOffGraphic;
            this.shuffleX = mediaLeftOffset - CONTROL_SIZE;
            final boolean shuffleHovered = hovered && mouseX >= this.shuffleX && mouseX <= this.shuffleX + CONTROL_SIZE && mouseY <= this.getScaledY() + CONTROL_SIZE;
            if(shuffleHovered) {
                renderer.drawRoundedRectangle(this.shuffleX, smallerGraphicY, CONTROL_SIZE, CONTROL_SIZE, 3, true, false, 0, BACKGROUND_COLOR, 0);
            }
            renderer.drawGraphicRectangle(shuffleGraphic, this.shuffleX, smallerGraphicY, CONTROL_SIZE, CONTROL_SIZE);
            mediaLeftOffset -= CONTROL_SIZE + 5;

            //loop
            final VectorGraphic loopGraphic = repeatStatus.equals("None") ? this.loopOffGraphic : repeatStatus.equals("Track") ? this.loopSameGraphic : this.loopAllGraphic;
            this.loopX = mediaRightOffset;
            final boolean loopHovered = hovered && mouseX >= this.loopX && mouseX <= this.loopX + CONTROL_SIZE && mouseY <= this.getScaledY() + CONTROL_SIZE;
            if(loopHovered) {
                renderer.drawRoundedRectangle(this.loopX, smallerGraphicY, CONTROL_SIZE, CONTROL_SIZE, 3, true, false, 0, BACKGROUND_COLOR, 0);
            }
            renderer.drawGraphicRectangle(loopGraphic, this.loopX, smallerGraphicY + 1, CONTROL_SIZE, CONTROL_SIZE);
            mediaRightOffset += CONTROL_SIZE + 5;

            matrixStack.popPose();
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if(!this.isHovered(mouseX, mouseY)) {
                return false;
            }

            //localize mouse pos
            mouseX -= SpotifyHudElement.this.getStartX();
            mouseY -= SpotifyHudElement.this.getStartY();
            mouseX -= this.getScaledX();
            mouseY -= this.getScaledY();
            mouseX = (int) (mouseX / this.getScale());
            mouseY = (int) (mouseY / this.getScale());

            //pause/play button
            if(mouseX >= this.playPauseX && mouseX <= this.playPauseX + PAUSE_PLAY_SIZE && mouseY <= this.getScaledY() + PAUSE_PLAY_SIZE) {
                playerctl("play-pause");
                return true;
            }

            if(mouseY > this.getScaledY() + CONTROL_SIZE) {
                return false;
            }

            //back button
            if(mouseX >= this.backX && mouseX <= this.backX + CONTROL_SIZE) {
                playerctl("previous");
                return true;
            }

            //next button
            if(mouseX >= this.nextX && mouseX <= this.nextX + CONTROL_SIZE && mouseY <= this.getScaledY() + CONTROL_SIZE) {
                playerctl("next");
                return true;
            }

            //shuffle button
            if(mouseX >= this.shuffleX && mouseX <= this.shuffleX + CONTROL_SIZE && mouseY <= this.getScaledY() + CONTROL_SIZE) {
                playerctl("shuffle Toggle");
                return true;
            }

            //loop button
            if(mouseX >= this.loopX && mouseX <= this.loopX + CONTROL_SIZE && mouseY <= this.getScaledY() + CONTROL_SIZE) {
                switch (repeatStatus) {
                    case "None" -> {
                        playerctl("loop Playlist");
                        return true;
                    }
                    case "Playlist" -> {
                        playerctl("loop Track");
                        return true;
                    }
                    case "Track" -> {
                        playerctl("loop None");
                        return true;
                    }
                }
            }

            return false;
        }

        @Override
        public double getHeight() {
            return this.height;
        }

        public void setHeight(double v) {
            this.height = v;
        }

    }

}