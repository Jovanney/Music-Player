import com.mpatric.mp3agic.InvalidDataException;
import com.mpatric.mp3agic.UnsupportedTagException;
import javazoom.jl.decoder.*;
import javazoom.jl.player.AudioDevice;
import javazoom.jl.player.FactoryRegistry;
import support.PlayerWindow;
import support.Song;
import javax.swing.*;
import javax.swing.event.MouseInputAdapter;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;
import java.util.Arrays;



public class Player {


    /**
     * The MPEG audio decoder.
     */

    //Janela do player
    public PlayerWindow window;

    private String[][] musicInfoList = new String[0][];

    /**
     * The AudioDevice where audio samples are written to.
     */
    private AudioDevice device;

    //decoder
    private Decoder decoder;


    /**
     * The MPEG audio bitstream.
     */
    private Bitstream bitstream;

    //Thread that plays the audio
    private SwingWorker playThread;

    private int currentFrame = 0;

    private boolean stopPressed = false;
    //Lista com as músicas
    private Song[] listaDeMusicas = new Song[0]; //return new Song(uuid, title, album, artist, year, strLength, msLength, filePath, fileSize, numFrames, msPerFrame);

    private String[][] listaMusicInfoReserva;

    private Song[] listaDeMusicasReserva;

    private Integer[] suffleIndexArray; //array com os indeces dps do shuffle

    private int playState = 0; //like the  useState from React

    public static String[][] delFromInfoMusic (String [][] sourceArray, int indexMusic){
        String[][] destinationArray  = new String[sourceArray.length - 1][];  //Criando novo Array bidimencional
        System.arraycopy(sourceArray, 0, destinationArray, 0, indexMusic); //Copiando os dados do sourceArray para o destionationArray
        System.arraycopy(sourceArray, indexMusic + 1, destinationArray, indexMusic, destinationArray.length - indexMusic);//Removendo a música do array destionation
        return destinationArray;
    }
    public final Song[] delFromListaDeMusicas( Song [] arr, int indexRemoved ){ //exatamente a mesma coisa de remover do array com as info das msc
        Song[] arrDestination = new Song[arr.length - 1];
        int remainingElements = arr.length - ( indexRemoved + 1 );
        System.arraycopy(arr, 0, arrDestination, 0, indexRemoved);
        System.arraycopy(arr, indexRemoved + 1, arrDestination, indexRemoved, remainingElements);
        return arrDestination;
    }
    public void changeLists() {
        // Array com os indexes
        Integer[] indexes = new Integer[listaDeMusicas.length];

        // inicializando array com indexes
        for (int i = 0; i < listaDeMusicas.length; i++) {
            indexes[i] = i;
        }

        // Shuffle the index array
        Random random = new Random();
        for (int i = indexes.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int temp = indexes[i];
            indexes[i] = indexes[j];
            indexes[j] = temp;
        }

        suffleIndexArray = indexes.clone();

        // variável para salvar o som que está tocando
        int currSongIndex;

        // se tiver alguma música tocando, eu coloco ela no começo da lista
        if (playThread != null && !playThread.isDone()) {
            shufflewhilePlaying = true;
            for (int i = 0; i < indexes.length; i++) {
                if (indexes[i] == indexMusic) {
                    // trocando o som que está tocando com o primeiro som da shuffled list
                    currSongIndex = indexes[0];
                    indexes[0] = indexes[i];
                    indexes[i] = currSongIndex;
                    break;
                }
            }
        }
        // deixando a lista de musica e de info com a msm ordem do shuffled index array
        for (int i = 0; i < listaDeMusicas.length; i++) {
            musicInfoList[i] = listaMusicInfoReserva[indexes[i]];
            listaDeMusicas[i] = listaDeMusicasReserva[indexes[i]];
        }
    }

    public static Integer[] removeElementInteger(Integer[] arr, int index) {
        // Create a new array with size one less than the original array
        Integer[] newArr = new Integer[arr.length - 1];

        // Copy all the elements from the original array to the new array, except the one at the specified index
        for (int i = 0, j = 0; i < arr.length; i++) {
            if (i != index) {
                newArr[j++] = arr[i];
            }
        }
        return newArr;
    }


    int listSize = 0;
    int indexMusic = 0;
    boolean lastSong = false;

    boolean loopPressed = false;

    boolean shufflePressed = false;

    boolean shufflewhilePlaying = false;

    int frameMouseClicked = -1; //começar com -1 pois não  temos frames negativos

    private final ActionListener buttonListenerPlayNow = e -> {
        stopPressed = false;
        lastSong = false;
        if (playThread != null && bitstream != null){ //checa se já não tem alguma thread rodando, caso tela finaliza ela antes de começar a nova
            currentFrame = 0; //zerando o frame para tocar a próxima música
            playThread.cancel(true);
            try {
                bitstream.close();
            } catch (BitstreamException ex) {
                throw new RuntimeException("Erro ao fechar bitstream antigo!",ex);
            }
            device.close();
        }
        playThread = new SwingWorker(){
            @Override
            protected Object doInBackground() throws Exception {
                String musicToBePlayed = window.getSelectedSong();
                int i = 0;
                for (Song song : listaDeMusicas){
                    if(song.getUuid().equals(musicToBePlayed)){
                        indexMusic = i;
                    }
                    i += 1;
                }
                while(indexMusic < listSize){ //While que toca todas as músicas da lista
                    Song song = listaDeMusicas[indexMusic];
                    stopPressed = false;
                    currentFrame = 0;
                    playState = 1; //Se achou, o botão play deve ficar como tocando
                    try {
                        device = FactoryRegistry.systemRegistry().createAudioDevice();
                        bitstream = new Bitstream(song.getBufferedInputStream());
                        device.open(decoder = new Decoder());
                    } catch (JavaLayerException | FileNotFoundException ex) {
                        throw new RuntimeException("Erro ao tocar a Música", ex);
                    }

                    //configurar os botões
                    window.setEnabledPlayPauseButton(Boolean.TRUE);
                    window.setPlayPauseButtonIcon(playState);
                    window.setEnabledStopButton(Boolean.TRUE);
                    window.setPlayingSongInfo(song.getTitle(), song.getAlbum(), song.getArtist());
                    window.setPlayingSongInfo(song.getTitle(), song.getAlbum(), song.getArtist());

                    while (currentFrame < song.getNumFrames() && !stopPressed) {
                        window.setEnabledScrubber(true); //Caso tenha alguma música na lista ele ativa o Scrubber

                        //Tocando o proximo frame
                        if (playState == 1) {
                            playNextFrame();
                            window.setTime((int) (currentFrame * song.getMsPerFrame()), (int) (song.getNumFrames() * song.getMsPerFrame())); //Colocando o tempo da música na tela
                            currentFrame += 1;
                        }
                        //Caso o botão shuffle tenha sido precionado
                        if(shufflewhilePlaying){
                            shufflewhilePlaying = false; // Resetando shuffle while playing
                            indexMusic = 0; //caso o botão shuffle tenha sido precionado enquanto toca, zeramos o indexMusic pois a música que estava tocando agora está na posição 0
                            window.setEnabledPreviousButton(false); //como a música será a primeira, devemos desativar o next button
                        }

                        window.setEnabledPreviousButton(indexMusic != 0); //Ativa o PreviousButton se a música não for a primeira
                        window.setEnabledNextButton(indexMusic != listSize - 1); //ativa o NextButton se a música não for a última

                        if ((currentFrame == song.getNumFrames())) { //quando a música chega em seu último frame ele troca a música
                            indexMusic += 1;
                        }
                        if(frameMouseClicked != -1) { //Se o frameMouseClicked mudou, significa que alteramos o cursor da música, logo o o frame atual agorea será o frame que a barra está
                            skipToFrame(frameMouseClicked);
                            frameMouseClicked = -1;
                        }
                        //caso o botão de loop esteja precionado e a musica atual seja a última da lista, o indexMusic volta a ser zero
                        if (loopPressed && indexMusic == listSize) {
                            indexMusic = 0;
                        }
                    }
                    window.resetMiniPlayer(); //Resetando o miniplayer pois a música Acabou
                }
                return null;
            }
        };
        playThread.execute();

    };
    private final ActionListener buttonListenerRemove = e -> {
        String musicToBeRemoved = window.getSelectedSong(); //uuid
        int indexMusicRemoved = 0;
        listSize -= 1;
        for (Song song : listaDeMusicas){
            if(song.getUuid().equals(musicToBeRemoved)){ //Checa se o uuid selecionado é igual ao uuid do Song atual
                musicInfoList = delFromInfoMusic(musicInfoList, indexMusicRemoved); //deleta da lista com as informações da música
                window.setQueueList(musicInfoList); //atualiza a janela
                listaDeMusicas = delFromListaDeMusicas(listaDeMusicas, indexMusicRemoved); //apaga da lista de músicas
                // Verifica se a lista de músicas reservadas não é nula
                if (shufflePressed) {
                    // Guarda o valor do índice a ser removido em uma variável auxiliar
                    int indexRemovidoAuxiliar = suffleIndexArray[indexMusicRemoved];

                    // Decrementa os índices maiores que o índice a ser removido para evitar out of bounds
                    for (int i = 0; i < suffleIndexArray.length; i++){
                        if (suffleIndexArray[i] > indexRemovidoAuxiliar){
                            suffleIndexArray[i]--;
                        }
                    }

                    // Remove o elemento das três listas
                    suffleIndexArray = removeElementInteger(suffleIndexArray, indexMusicRemoved);
                    listaMusicInfoReserva = delFromInfoMusic(listaMusicInfoReserva, indexRemovidoAuxiliar);
                    listaDeMusicasReserva = delFromListaDeMusicas(listaDeMusicasReserva, indexRemovidoAuxiliar);
                }

                break;

            }else{
                indexMusicRemoved += 1;
            }

        }
        if( indexMusic == indexMusicRemoved){ //Se a música removida é a que está tocando ele para a música atual e reseta o miniplayer
            stopPressed = true;
            window.resetMiniPlayer();
        }
        if (indexMusic > indexMusicRemoved){ //Se a música removida vem depois da mpusica que está tocando agora ele decrementa um no index para não ir para a próxima música
            indexMusic -= 1;
        }
        if (listSize < 2) {
            window.setEnabledShuffleButton(false);
        } //desativando o botão de shuffle
        if (listaDeMusicas.length < 1) {
            window.setEnabledLoopButton(false);
        } //desativando o botão de loop

    };
    private final ActionListener buttonListenerAddSong = e -> {
        Song newMusic;
        try {
            newMusic = window.openFileChooser();
        }catch (IOException | BitstreamException | InvalidDataException | UnsupportedTagException ex){
            throw new RuntimeException("Erro ao Adicionar Música", ex); //save the error that can be acess with getCause() & getMessage()
        }
        if (newMusic != null) {
            int musicIndex = musicInfoList.length; //Pegando o indice da música
            listSize += 1;
            musicInfoList = Arrays.copyOf(musicInfoList, musicIndex + 1); //Criando uma cópia do array InfoList e aumentando o tamanho dela em 1
            musicInfoList[musicIndex] = newMusic.getDisplayInfo(); //Adicionando a musica no array
            this.window.setQueueList(musicInfoList); // Adicionando ela na tela

            //adicionando na lista de musicas agora
            listaDeMusicas = Arrays.copyOf(listaDeMusicas, musicIndex + 1);
            listaDeMusicas[musicIndex] = newMusic;

            if (shufflePressed) {
                listaMusicInfoReserva = Arrays.copyOf(listaMusicInfoReserva, musicIndex + 1);
                listaMusicInfoReserva[musicIndex] = newMusic.getDisplayInfo();
                listaDeMusicasReserva = Arrays.copyOf(listaDeMusicasReserva, musicIndex + 1);
                listaDeMusicasReserva[musicIndex] = newMusic;
            }

            if (listaDeMusicas.length >= 2) {
                window.setEnabledShuffleButton(true);
            } //Ativando o botão de shuffle
            if (listaDeMusicas.length > 0) {
                window.setEnabledLoopButton(true);
            } //Ativando o botão de loop


        }
    };
    private final ActionListener buttonListenerPlayPause = e -> {
        if(playState == 0){ //se está pausado fica tocando, senão fica pausado
            playState = 1;
        }else{
            playState = 0;
        }
        window.setPlayPauseButtonIcon(playState);
    };
    private final ActionListener buttonListenerStop = e -> {
        playState = 0;
        window.setPlayPauseButtonIcon(playState); //seta o botão de play para 0
        window.setEnabledPlayPauseButton(false); //desativa o botão de plau
        window.setEnabledStopButton(Boolean.FALSE); //desativa o botão de strop
        window.resetMiniPlayer();
        stopPressed = true;
        loopPressed = false;
        indexMusic = listSize;
        playThread.cancel(true); //interrrompe a thread
    };


    private final ActionListener buttonListenerNext = e -> {
        indexMusic += 1;
        stopPressed = true;
    };
    private final ActionListener buttonListenerPrevious = e -> {
        indexMusic -= 1;
        stopPressed = true;
    };
    private final ActionListener buttonListenerShuffle = e -> {

        shufflePressed = !shufflePressed; //mudando o estado do shuffle

        // Se shufflePressed  a lista de músicas e a lista de informações das músicas são clonadas em duas variáveis de reserva e então o método "changeLists()" é chamado para embaralhar as listas.
        if (shufflePressed){
            listaDeMusicasReserva = listaDeMusicas.clone();
            listaMusicInfoReserva = musicInfoList.clone();

            changeLists();
        }else{
            // Se shufflePressed for falso, as listas de informações das músicas e a lista de músicas são restauradas para seus valores de reserva e o índice atual da música é alterado para o índice equivalente da lista original.
            musicInfoList = listaMusicInfoReserva.clone();
            listaDeMusicas = listaDeMusicasReserva.clone();

            indexMusic = suffleIndexArray[indexMusic];
        }
        this.window.setQueueList(musicInfoList);
    };
    private final ActionListener buttonListenerLoop = e -> {
        loopPressed = !loopPressed;
    };
    private final MouseInputAdapter scrubberMouseInputAdapter = new MouseInputAdapter() {
        @Override
        public void mouseReleased(MouseEvent e) {

        }

        @Override
        public void mousePressed(MouseEvent e) {
            frameMouseClicked = (int) (window.getScrubberValue()/listaDeMusicas[indexMusic].getMsPerFrame());
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            frameMouseClicked = (int) (window.getScrubberValue()/listaDeMusicas[indexMusic].getMsPerFrame());
        }
    };

    public Player() {
        EventQueue.invokeLater(() -> window = new PlayerWindow(
                "Spotify pirata",
                musicInfoList, //FILA_COMO_ARRAY_BIDIMENSIONAL
                buttonListenerPlayNow,
                buttonListenerRemove,
                buttonListenerAddSong,
                buttonListenerShuffle,
                buttonListenerPrevious,
                buttonListenerPlayPause,
                buttonListenerStop,
                buttonListenerNext,
                buttonListenerLoop,
                scrubberMouseInputAdapter)

        );
    }
    //<editor-fold desc="Essential">

    /**
     * @return False if there are no more frames to play.
     */
    private boolean playNextFrame() throws JavaLayerException {
        // TODO: Is this thread safe?
        if (device != null) {
            Header h = bitstream.readFrame();
            if (h == null) return false;

            SampleBuffer output = (SampleBuffer) decoder.decodeFrame(h, bitstream);
            device.write(output.getBuffer(), 0, output.getBufferLength());
            bitstream.closeFrame();
        }
        return true;
    }

    /**
     * @return False if there are no more frames to skip.
     */
    private boolean skipNextFrame() throws BitstreamException {
        // TODO: Is this thread safe?
        Header h = bitstream.readFrame();
        if (h == null) return false;
        bitstream.closeFrame();
        currentFrame++;
        return true;
    }

    /**
     * Skips bitstream to the target frame if the new frame is higher than the current one.
     *
     * @param newFrame Frame to skip to.
     * @throws BitstreamException Generic Bitstream exception.
     */
    private void skipToFrame(int newFrame) throws BitstreamException {
        // TODO: Is this thread safe?
        //Colocar  < pois só estava mudando para frames após o atual
        if (newFrame < currentFrame) {
            device.close(); //Fechando o device
            try { //Criando Audio Device, Decoder e Bitstream Novamente
                device = FactoryRegistry.systemRegistry().createAudioDevice();
                bitstream.close();
                device.open(decoder = new Decoder());
                bitstream = new Bitstream(listaDeMusicas[indexMusic].getBufferedInputStream());
            } catch (JavaLayerException | FileNotFoundException ex) {
                throw new RuntimeException(ex);
            }
            currentFrame = 0; //Resetando currentFrame
        }
        //Se o newFrame for maior funciona normalmente, mas se for menor o currentFrame vai para 0 e o device, bitstream e Decode são reinicialziados
        int framesToSkip = newFrame - currentFrame;
        boolean condition = true;
        while (framesToSkip-- > 0 && condition) condition = skipNextFrame();
    }
    //</editor-fold>

}