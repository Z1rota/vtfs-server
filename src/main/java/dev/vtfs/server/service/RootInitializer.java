package dev.vtfs.server.service;

import dev.vtfs.server.entity.FsInode;
import dev.vtfs.server.entity.FsNodeType;
import dev.vtfs.server.repository.InodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class RootInitializer implements ApplicationRunner {

    private final InodeRepository inodeRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        if (inodeRepository.findRoot().isPresent()) {
            return;
        }
        FsInode root = new FsInode(FsNodeType.DIRECTORY,0755,true);
        inodeRepository.save(root);
    }

}
